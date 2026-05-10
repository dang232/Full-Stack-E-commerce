package com.vnshop.orderservice.domain;

import java.time.Instant;
import java.util.Objects;

public record Invoice(
        String id,
        String orderId,
        Long subOrderId,
        String buyerId,
        String sellerId,
        String objectKey,
        String checksumSha256,
        int version,
        Instant generatedAt
) {
    public Invoice {
        requireNonBlank(id, "id");
        requireNonBlank(orderId, "orderId");
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
