package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentIdempotencyKey;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import com.vnshop.paymentservice.domain.port.out.PaymentIdempotencyKeyRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.application.ledger.LedgerService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

public class ProcessPaymentUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final LedgerService ledgerService;
    private final PaymentIdempotencyKeyRepositoryPort idempotencyKeyRepository;
    private final Clock clock;

    public ProcessPaymentUseCase(
            PaymentRepositoryPort paymentRepositoryPort,
            PaymentGatewayPort paymentGatewayPort,
            LedgerService ledgerService,
            PaymentIdempotencyKeyRepositoryPort idempotencyKeyRepository
    ) {
        this(paymentRepositoryPort, paymentGatewayPort, ledgerService, idempotencyKeyRepository, Clock.systemUTC());
    }

    public ProcessPaymentUseCase(
            PaymentRepositoryPort paymentRepositoryPort,
            PaymentGatewayPort paymentGatewayPort,
            LedgerService ledgerService,
            PaymentIdempotencyKeyRepositoryPort idempotencyKeyRepository,
            Clock clock
    ) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
        this.paymentGatewayPort = Objects.requireNonNull(paymentGatewayPort, "paymentGatewayPort is required");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService is required");
        this.idempotencyKeyRepository = Objects.requireNonNull(idempotencyKeyRepository, "idempotencyKeyRepository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

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
        PaymentGatewayPort.GatewayPaymentResult result = paymentGatewayPort.processPayment(pendingPayment);
        Payment savedPayment = paymentRepositoryPort.save(pendingPayment.withResult(result.status(), result.transactionRef()));
        if (savedPayment.status() == PaymentStatus.COMPLETED) {
            ledgerService.recordPayment(savedPayment);
        }

        if (idempotencyKey != null) {
            idempotencyKeyRepository.save(new PaymentIdempotencyKey(
                    idempotencyKey,
                    savedPayment.paymentId(),
                    requestHash,
                    Instant.now(clock)
            ));
        }
        return savedPayment;
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
