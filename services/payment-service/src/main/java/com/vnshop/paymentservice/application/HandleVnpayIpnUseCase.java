package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;

import java.util.Objects;

public class HandleVnpayIpnUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;

    public HandleVnpayIpnUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
    }

    public Payment applyVerifiedResult(String paymentId, PaymentStatus paymentStatus, String transactionRef) {
        if (paymentStatus == PaymentStatus.PENDING) {
            throw new IllegalArgumentException("VNPay IPN must resolve to a terminal payment status");
        }
        Payment payment = paymentRepositoryPort.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("payment not found: " + paymentId));
        if (payment.method() != Payment.Method.VNPAY) {
            throw new IllegalArgumentException("payment is not VNPay: " + paymentId);
        }
        if (payment.status() != PaymentStatus.PENDING) {
            return payment;
        }
        return paymentRepositoryPort.save(payment.withResult(paymentStatus, transactionRef));
    }
}
