package com.vnshop.userservice.domain;

import java.time.Instant;
import java.util.Objects;

public record WishlistItem(String keycloakId, String productId, Instant createdAt) {
    public WishlistItem {
        Objects.requireNonNull(keycloakId, "keycloakId is required");
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (keycloakId.isBlank()) {
            throw new IllegalArgumentException("keycloakId is required");
        }
        if (productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
    }
}
