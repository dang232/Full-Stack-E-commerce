package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.infrastructure.ledger.LedgerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@ConditionalOnProperty(name = "payment.mode", havingValue = "live")
public class MomoCallbackService {
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final LedgerService ledgerService;
    private final MomoGateway momoGateway;

    public MomoCallbackService(PaymentRepositoryPort paymentRepositoryPort, LedgerService ledgerService, MomoGateway momoGateway) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService is required");
        this.momoGateway = Objects.requireNonNull(momoGateway, "momoGateway is required");
    }

    @Transactional
    public MomoIpnResult handleIpn(MomoIpnRequest request) {
        MomoGateway.MomoVerification verification = momoGateway.verifyIpn(request);
        if (!verification.validSignature()) {
            return MomoIpnResult.invalidSignature();
        }

        Payment payment = paymentRepositoryPort.findById(verification.paymentId()).orElse(null);
        if (payment == null) {
            return MomoIpnResult.paymentNotFound();
        }
        if (payment.method() != Payment.Method.MOMO) {
            return MomoIpnResult.paymentNotFound();
        }
        if (payment.status() == PaymentStatus.COMPLETED) {
            return MomoIpnResult.success();
        }

        Payment updatedPayment = payment.withResult(verification.status(), verification.transactionNo());
        paymentRepositoryPort.save(updatedPayment);
        if (verification.status() == PaymentStatus.COMPLETED) {
            ledgerService.recordPayment(verification.transactionNo(), payment.orderId(), payment.amount());
        }
        return MomoIpnResult.success();
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
