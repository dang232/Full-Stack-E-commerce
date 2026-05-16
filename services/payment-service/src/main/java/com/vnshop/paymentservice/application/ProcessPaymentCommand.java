package com.vnshop.paymentservice.application;

import java.math.BigDecimal;

public record ProcessPaymentCommand(
        String orderId,
        String buyerId,
        BigDecimal amount,
        PaymentMethodInput method,
        String idempotencyKey
) {
    public ProcessPaymentCommand(String orderId, String buyerId, BigDecimal amount, PaymentMethodInput method) {
        this(orderId, buyerId, amount, method, null);
    }
}
