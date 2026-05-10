package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;

import java.math.BigDecimal;
import java.util.Objects;

public class ProcessPaymentUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;

    public ProcessPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort, PaymentGatewayPort paymentGatewayPort) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
        this.paymentGatewayPort = Objects.requireNonNull(paymentGatewayPort, "paymentGatewayPort is required");
    }

    public Payment process(String orderId, String buyerId, BigDecimal amount, Payment.Method method) {
        Payment pendingPayment = Payment.pending(orderId, buyerId, amount, method);
        PaymentGatewayPort.GatewayPaymentResult result = paymentGatewayPort.processPayment(pendingPayment);
        return paymentRepositoryPort.save(pendingPayment.withResult(result.status(), result.transactionRef()));
    }
}
