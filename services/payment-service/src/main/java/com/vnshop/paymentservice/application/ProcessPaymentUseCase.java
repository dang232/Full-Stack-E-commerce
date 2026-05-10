package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.application.ledger.LedgerService;

import java.math.BigDecimal;
import java.util.Objects;

public class ProcessPaymentUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final LedgerService ledgerService;

    public ProcessPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort, PaymentGatewayPort paymentGatewayPort, LedgerService ledgerService) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
        this.paymentGatewayPort = Objects.requireNonNull(paymentGatewayPort, "paymentGatewayPort is required");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService is required");
    }

    public Payment process(String orderId, String buyerId, BigDecimal amount, Payment.Method method) {
        Payment pendingPayment = Payment.pending(orderId, buyerId, amount, method);
        PaymentGatewayPort.GatewayPaymentResult result = paymentGatewayPort.processPayment(pendingPayment);
        Payment savedPayment = paymentRepositoryPort.save(pendingPayment.withResult(result.status(), result.transactionRef()));
        if (savedPayment.status() == PaymentStatus.COMPLETED) {
            ledgerService.recordPayment(savedPayment);
        }
        return savedPayment;
    }
}
