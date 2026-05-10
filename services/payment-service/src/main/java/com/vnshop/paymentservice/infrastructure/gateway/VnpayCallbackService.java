package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.application.ledger.LedgerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

@Service
@ConditionalOnProperty(name = "payment.mode", havingValue = "live")
public class VnpayCallbackService {
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final LedgerService ledgerService;
    private final VnpayGateway vnpayGateway;

    public VnpayCallbackService(PaymentRepositoryPort paymentRepositoryPort, LedgerService ledgerService, VnpayGateway vnpayGateway) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService is required");
        this.vnpayGateway = Objects.requireNonNull(vnpayGateway, "vnpayGateway is required");
    }

    @Transactional
    public VnpayIpnResult handleIpn(Map<String, String> parameters) {
        VnpayGateway.VnpayVerification verification = vnpayGateway.verify(parameters);
        if (!verification.validSignature()) {
            return VnpayIpnResult.invalidSignature();
        }

        Payment payment = paymentRepositoryPort.findById(verification.paymentId()).orElse(null);
        if (payment == null) {
            return VnpayIpnResult.paymentNotFound();
        }
        if (payment.status() == PaymentStatus.COMPLETED) {
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
        return VnpayIpnResult.success();
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
