package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;

import java.util.Objects;

public class GetPaymentStatusUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;

    public GetPaymentStatusUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
    }

    public Payment getByOrderId(String orderId) {
        return paymentRepositoryPort.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("payment not found for order: " + orderId));
    }
}
