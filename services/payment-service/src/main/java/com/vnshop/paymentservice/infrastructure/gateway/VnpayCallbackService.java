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
public class VnpayCallbackService {
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final LedgerService ledgerService;
    private final VnpayGateway vnpayGateway;
    private final PaymentCallbackLogStore callbackLogStore;
    private final PaymentCallbackOutbox outbox;

    public VnpayCallbackService(PaymentRepositoryPort paymentRepositoryPort, LedgerService ledgerService, VnpayGateway vnpayGateway, PaymentCallbackLogStore callbackLogStore, PaymentCallbackOutbox outbox) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService is required");
        this.vnpayGateway = Objects.requireNonNull(vnpayGateway, "vnpayGateway is required");
        this.callbackLogStore = Objects.requireNonNull(callbackLogStore, "callbackLogStore is required");
        this.outbox = Objects.requireNonNull(outbox, "outbox is required");
    }

    @Transactional
    public VnpayIpnResult handleIpn(Map<String, String> parameters) {
        return handleIpn(parameters, Map.of());
    }

    @Transactional
    public VnpayIpnResult handleIpn(Map<String, String> parameters, Map<String, String> headers) {
        String body = PaymentCallbackHasher.canonical(parameters);
        String payloadHash = PaymentCallbackHasher.sha256(body);
        String signatureHash = PaymentCallbackHasher.sha256(parameters.get("vnp_SecureHash"));
        PaymentCallbackAttempt duplicate = callbackLogStore.findProcessed("VNPAY", parameters.get("vnp_TransactionNo"), payloadHash, signatureHash).orElse(null);
        if (duplicate != null) {
            callbackLogStore.save(attempt(parameters, headers, payloadHash, signatureHash, duplicate.processingStatus(), true));
            return VnpayIpnResult.success();
        }

        VnpayGateway.VnpayVerification verification = vnpayGateway.verify(parameters);
        if (!verification.validSignature()) {
            callbackLogStore.save(attempt(parameters, headers, payloadHash, signatureHash, "INVALID_SIGNATURE", false));
            return VnpayIpnResult.invalidSignature();
        }

        Payment payment = paymentRepositoryPort.findById(verification.paymentId()).orElse(null);
        if (payment == null) {
            callbackLogStore.save(attempt(parameters, headers, payloadHash, signatureHash, "PAYMENT_NOT_FOUND", false));
            return VnpayIpnResult.paymentNotFound();
        }
        if (payment.status() == PaymentStatus.COMPLETED) {
            callbackLogStore.save(attempt(parameters, headers, payloadHash, signatureHash, "PROCESSED", false));
            return VnpayIpnResult.success();
        }

        PaymentStatus status = verification.status();
        String transactionRef = verification.transactionNo() == null || verification.transactionNo().isBlank()
                ? verification.paymentId()
                : verification.transactionNo();
        Payment updatedPayment = payment.withResult(status, transactionRef);
        paymentRepositoryPort.save(updatedPayment);
        if (status == PaymentStatus.COMPLETED) {
            ledgerService.recordPayment(transactionRef, payment.orderId(), payment.amount());
        }
        PaymentCallbackAttempt savedAttempt = callbackLogStore.save(attempt(parameters, headers, payloadHash, signatureHash, status == PaymentStatus.COMPLETED ? "PROCESSED" : "FAILED", false));
        outbox.save(PaymentCallbackOutboxRecord.pending("VNPAY", payment.paymentId(), payment.orderId(), transactionRef, status.name(), payment.amount(), savedAttempt.callbackId(), savedAttempt.eventId(), savedAttempt.payloadHash()));
        return VnpayIpnResult.success();
    }

    private PaymentCallbackAttempt attempt(Map<String, String> parameters, Map<String, String> headers, String payloadHash, String signatureHash, String processingStatus, boolean duplicateReplay) {
        return new PaymentCallbackAttempt(
                UUID.randomUUID().toString(),
                "VNPAY",
                parameters.get("vnp_TransactionNo"),
                payloadHash,
                signatureHash,
                PaymentCallbackHasher.canonical(headers),
                PaymentCallbackHasher.canonical(parameters),
                Instant.now(),
                processingStatus,
                duplicateReplay
        );
    }

    public VnpayGateway.VnpayVerification verifyReturn(Map<String, String> parameters) {
        return vnpayGateway.verify(parameters);
    }

    public record VnpayIpnResult(String responseCode, String message) {
        static VnpayIpnResult success() {
            return new VnpayIpnResult("00", "Confirm Success");
        }

        static VnpayIpnResult invalidSignature() {
            return new VnpayIpnResult("97", "Invalid Checksum");
        }

        static VnpayIpnResult paymentNotFound() {
            return new VnpayIpnResult("01", "Order not Found");
        }
    }
}
