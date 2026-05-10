package com.vnshop.paymentservice.infrastructure.gateway;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentCallbackOutboxRecord(
        Long id,
        String provider,
        String paymentId,
        String orderId,
        String transactionRef,
        String status,
        BigDecimal amount,
        String currency,
        String callbackId,
        String callbackEventId,
        String payloadHash,
        Instant createdAt,
        Instant publishedAt
) {
    public static PaymentCallbackOutboxRecord pending(String provider, String paymentId, String orderId, String transactionRef, String status, BigDecimal amount, String callbackId, String callbackEventId, String payloadHash) {
        return new PaymentCallbackOutboxRecord(null, provider, paymentId, orderId, transactionRef, status, amount, "VND", callbackId, callbackEventId, payloadHash, Instant.now(), null);
    }
}
