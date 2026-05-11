package com.vnshop.orderservice.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Invoice(
        UUID id,
        UUID orderId,
        Long subOrderId,
        String buyerId,
        String sellerId,
        String objectKey,
        String checksumSha256,
        int version,
        Instant generatedAt
) {
    public Invoice {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(subOrderId, "subOrderId is required");
        requireNonBlank(buyerId, "buyerId");
        requireNonBlank(sellerId, "sellerId");
        requireNonBlank(objectKey, "objectKey");
        requireNonBlank(checksumSha256, "checksumSha256");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(generatedAt, "generatedAt is required");
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
