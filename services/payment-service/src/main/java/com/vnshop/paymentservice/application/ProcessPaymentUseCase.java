package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentIdempotencyKey;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import com.vnshop.paymentservice.domain.port.out.PaymentIdempotencyKeyRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.application.ledger.LedgerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

public class ProcessPaymentUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessPaymentUseCase.class);

    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final LedgerService ledgerService;
    private final PaymentIdempotencyKeyRepositoryPort idempotencyKeyRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ProcessPaymentUseCase(
            PaymentRepositoryPort paymentRepositoryPort,
            PaymentGatewayPort paymentGatewayPort,
            LedgerService ledgerService,
            PaymentIdempotencyKeyRepositoryPort idempotencyKeyRepository,
            TransactionTemplate transactionTemplate
    ) {
        this(paymentRepositoryPort, paymentGatewayPort, ledgerService, idempotencyKeyRepository, transactionTemplate, Clock.systemUTC());
    }

    public ProcessPaymentUseCase(
            PaymentRepositoryPort paymentRepositoryPort,
            PaymentGatewayPort paymentGatewayPort,
            LedgerService ledgerService,
            PaymentIdempotencyKeyRepositoryPort idempotencyKeyRepository,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
        this.paymentGatewayPort = Objects.requireNonNull(paymentGatewayPort, "paymentGatewayPort is required");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService is required");
        this.idempotencyKeyRepository = Objects.requireNonNull(idempotencyKeyRepository, "idempotencyKeyRepository is required");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /**
     * Order of operations is correctness-critical:
     *
     * <ol>
     *   <li>Look up the idempotency key. A previous successful run short-circuits to the cached payment;
     *       a key reused with a different request body throws {@link IdempotencyKeyConflictException}.</li>
     *   <li>Call the gateway <strong>outside</strong> any DB transaction. Gateway charges are real-world
     *       side effects and cannot be rolled back by deleting a row. Doing this first means a retry that
     *       reaches step 3 is the only kind of failure that risks orphaning a charge — and step 3 is
     *       atomic.</li>
     *   <li>Open a single TX and persist (a) the {@link Payment}, (b) the ledger entries when the gateway
     *       reports a terminal {@code COMPLETED} status, and (c) the {@link PaymentIdempotencyKey}.
     *       Everything in this block commits or rolls back together, so a retry with the same key never
     *       sees a partially-recorded state.</li>
     *   <li>If step 3 throws, the gateway charge is real but unrecorded. We log loudly with paymentId,
     *       orderId, and the gateway transaction ref so the existing reconciliation worker (or a human
     *       on-call) can pick up the orphan.</li>
     * </ol>
     */
    public Payment process(ProcessPaymentCommand command) {
        String idempotencyKey = normalize(command.idempotencyKey());
        String requestHash = idempotencyKey == null ? null : computeRequestHash(command);

        if (idempotencyKey != null) {
            Optional<PaymentIdempotencyKey> existing = idempotencyKeyRepository.findByKey(idempotencyKey);
            if (existing.isPresent()) {
                PaymentIdempotencyKey stored = existing.get();
                if (!stored.requestHash().equals(requestHash)) {
                    throw new IdempotencyKeyConflictException(
                            "Idempotency-Key reused with a different request body"
                    );
                }
                return paymentRepositoryPort.findById(stored.paymentId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Idempotency record references missing payment: " + stored.paymentId()
                        ));
            }
        }

        Payment pendingPayment = Payment.pending(command.orderId(), command.buyerId(), command.amount(), toDomain(command.method()));
        // Gateway side-effect runs OUTSIDE the TX. A successful charge whose persistence later rolls
        // back is recoverable via reconciliation; a charge that's "rolled back" inside a TX boundary
        // is impossible — the bank already has the money.
        PaymentGatewayPort.GatewayPaymentResult result = paymentGatewayPort.processPayment(pendingPayment);

        try {
            String finalIdempotencyKey = idempotencyKey;
            String finalRequestHash = requestHash;
            return transactionTemplate.execute(status -> {
                Payment savedPayment = paymentRepositoryPort.save(
                        pendingPayment.withResult(result.status(), result.transactionRef())
                );
                if (savedPayment.status() == PaymentStatus.COMPLETED) {
                    ledgerService.recordPayment(savedPayment);
                }
                if (finalIdempotencyKey != null) {
                    idempotencyKeyRepository.save(new PaymentIdempotencyKey(
                            finalIdempotencyKey,
                            savedPayment.paymentId(),
                            finalRequestHash,
                            Instant.now(clock)
                    ));
                }
                return savedPayment;
            });
        } catch (RuntimeException ex) {
            // Gateway already charged. Persistence failed. Surface the orphan loudly so the
            // reconciliation worker / on-call can recover. Caller still sees the failure and may
            // retry — the missing idempotency key means the retry attempts a fresh charge, which
            // is the correct UX trade-off only because this branch is rare and observable.
            log.error(
                    "payment-orphan: gateway charge succeeded but persistence failed. "
                            + "paymentId={} orderId={} buyerId={} gatewayTxnRef={} gatewayStatus={}",
                    pendingPayment.paymentId(),
                    command.orderId(),
                    command.buyerId(),
                    result.transactionRef(),
                    result.status(),
                    ex
            );
            throw ex;
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Hash composition: SHA-256 over {@code orderId|buyerId|amount|method}.
     * Matches the documented contract — same body + same key returns the cached
     * payment, different body + same key returns a 422 conflict.
     */
    private static String computeRequestHash(ProcessPaymentCommand command) {
        String canonical = String.join("|",
                String.valueOf(command.orderId()),
                String.valueOf(command.buyerId()),
                command.amount() == null ? "null" : command.amount().stripTrailingZeros().toPlainString(),
                command.method() == null ? "null" : command.method().name()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private PaymentMethod toDomain(PaymentMethodInput method) {
        return switch (method) {
            case COD -> PaymentMethod.COD;
            case VNPAY -> PaymentMethod.VNPAY;
            case MOMO -> PaymentMethod.MOMO;
        };
    }
}
