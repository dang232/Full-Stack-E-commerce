package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.application.ledger.LedgerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "payment.mode", havingValue = "live")
public class MomoCallbackService {
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final LedgerService ledgerService;
    private final MomoGateway momoGateway;
    private final PaymentCallbackLogStore callbackLogStore;
    private final PaymentCallbackOutbox outbox;

    public MomoCallbackService(PaymentRepositoryPort paymentRepositoryPort, LedgerService ledgerService, MomoGateway momoGateway, PaymentCallbackLogStore callbackLogStore, PaymentCallbackOutbox outbox) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService is required");
        this.momoGateway = Objects.requireNonNull(momoGateway, "momoGateway is required");
        this.callbackLogStore = Objects.requireNonNull(callbackLogStore, "callbackLogStore is required");
        this.outbox = Objects.requireNonNull(outbox, "outbox is required");
    }

    @Transactional
    public MomoIpnResult handleIpn(MomoIpnRequest request) {
        return handleIpn(request, Map.of());
    }

    @Transactional
    public MomoIpnResult handleIpn(MomoIpnRequest request, Map<String, String> headers) {
        String body = PaymentCallbackHasher.canonical(MomoSigner.orderedIpnParameters(request));
        String payloadHash = PaymentCallbackHasher.sha256(body);
        String signatureHash = PaymentCallbackHasher.sha256(request.signature());
        PaymentCallbackAttempt duplicate = callbackLogStore.findProcessed("MOMO", String.valueOf(request.transId()), payloadHash, signatureHash).orElse(null);
        if (duplicate != null) {
            callbackLogStore.save(attempt(request, headers, payloadHash, signatureHash, duplicate.processingStatus(), true));
            return MomoIpnResult.success();
        }

        MomoGateway.MomoVerification verification = momoGateway.verifyIpn(request);
        if (!verification.validSignature()) {
            callbackLogStore.save(attempt(request, headers, payloadHash, signatureHash, "INVALID_SIGNATURE", false));
            return MomoIpnResult.invalidSignature();
        }

        Payment payment = paymentRepositoryPort.findById(verification.paymentId()).orElse(null);
        if (payment == null) {
            callbackLogStore.save(attempt(request, headers, payloadHash, signatureHash, "PAYMENT_NOT_FOUND", false));
            return MomoIpnResult.paymentNotFound();
        }
        if (payment.method() != Payment.Method.MOMO) {
            callbackLogStore.save(attempt(request, headers, payloadHash, signatureHash, "PAYMENT_NOT_FOUND", false));
            return MomoIpnResult.paymentNotFound();
        }
        if (payment.status() == PaymentStatus.COMPLETED) {
            callbackLogStore.save(attempt(request, headers, payloadHash, signatureHash, "PROCESSED", false));
            return MomoIpnResult.success();
        }

        Payment updatedPayment = payment.withResult(verification.status(), verification.transactionNo());
        paymentRepositoryPort.save(updatedPayment);
        if (verification.status() == PaymentStatus.COMPLETED) {
            ledgerService.recordPayment(verification.transactionNo(), payment.orderId(), payment.amount());
        }
        PaymentCallbackAttempt savedAttempt = callbackLogStore.save(attempt(request, headers, payloadHash, signatureHash, verification.status() == PaymentStatus.COMPLETED ? "PROCESSED" : "FAILED", false));
        outbox.save(PaymentCallbackOutboxRecord.pending("MOMO", payment.paymentId(), payment.orderId(), verification.transactionNo(), verification.status().name(), payment.amount(), savedAttempt.callbackId(), savedAttempt.eventId(), savedAttempt.payloadHash()));
        return MomoIpnResult.success();
    }

    private PaymentCallbackAttempt attempt(MomoIpnRequest request, Map<String, String> headers, String payloadHash, String signatureHash, String processingStatus, boolean duplicateReplay) {
        return new PaymentCallbackAttempt(
                UUID.randomUUID().toString(),
                "MOMO",
                String.valueOf(request.transId()),
                payloadHash,
                signatureHash,
                PaymentCallbackHasher.canonical(headers),
                PaymentCallbackHasher.canonical(MomoSigner.orderedIpnParameters(request)),
                Instant.now(),
                processingStatus,
                duplicateReplay
        );
    }

    public record MomoIpnResult(int resultCode, String message) {
        static MomoIpnResult success() {
            return new MomoIpnResult(0, "Confirm Success");
        }

        static MomoIpnResult invalidSignature() {
            return new MomoIpnResult(97, "Invalid Signature");
        }

        static MomoIpnResult paymentNotFound() {
            return new MomoIpnResult(1, "Order not Found");
        }
    }
}
