package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.application.ledger.LedgerService;

import java.util.Objects;

public class HandleVnpayIpnUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final LedgerService ledgerService;

    public HandleVnpayIpnUseCase(PaymentRepositoryPort paymentRepositoryPort, LedgerService ledgerService) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService is required");
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
        Payment savedPayment = paymentRepositoryPort.save(payment.withResult(paymentStatus, transactionRef));
        if (savedPayment.status() == PaymentStatus.COMPLETED) {
            ledgerService.recordPayment(savedPayment);
        }
        return savedPayment;
    }
}
