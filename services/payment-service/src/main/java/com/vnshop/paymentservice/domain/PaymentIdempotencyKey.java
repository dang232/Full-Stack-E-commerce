package com.vnshop.paymentservice.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentIdempotencyKey(String key, UUID paymentId, String requestHash, Instant createdAt) {
    public PaymentIdempotencyKey {
        Objects.requireNonNull(key, "key is required");
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(requestHash, "requestHash is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }
}
