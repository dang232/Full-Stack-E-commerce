package com.vnshop.paymentservice.infrastructure.web;

import com.vnshop.paymentservice.domain.Payment;

import java.math.BigDecimal;

public record PayPalCreateResponse(
        PaymentResponse payment,
        String clientId,
        String paypalOrderId,
        String status,
        BigDecimal externalAmount,
        String externalCurrency,
        BigDecimal fxRate) {
    public static PayPalCreateResponse of(
            Payment payment,
            String clientId,
            String paypalOrderId,
            String status,
            BigDecimal externalAmount,
            String externalCurrency,
            BigDecimal fxRate) {
        return new PayPalCreateResponse(
                PaymentResponse.fromDomain(payment),
                clientId,
                paypalOrderId,
                status,
                externalAmount,
                externalCurrency,
                fxRate);
    }
}
