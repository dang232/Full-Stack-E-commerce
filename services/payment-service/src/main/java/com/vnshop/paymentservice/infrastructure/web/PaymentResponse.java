package com.vnshop.paymentservice.infrastructure.web;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String paymentId,
        String orderId,
        String buyerId,
        BigDecimal amount,
        String method,
        String status,
        String transactionRef,
        Instant createdAt
) {
    static PaymentResponse fromDomain(Payment payment) {
        return new PaymentResponse(
                payment.paymentId().toString(),
                payment.orderId(),
                payment.buyerId(),
                payment.amount(),
                payment.method().name(),
                payment.status().name(),
                payment.transactionRef(),
                payment.createdAt()
        );
    }
}
