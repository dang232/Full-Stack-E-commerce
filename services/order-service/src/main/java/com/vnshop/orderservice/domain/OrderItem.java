package com.vnshop.orderservice.domain;

import java.util.Objects;

public record OrderItem(
        String productId,
        String variantSku,
        String sellerId,
        String name,
        int quantity,
        Money unitPrice,
        String imageUrl
) {
    public OrderItem {
        requireNonBlank(productId, "productId");
        requireNonBlank(variantSku, "variantSku");
        requireNonBlank(sellerId, "sellerId");
        requireNonBlank(name, "name");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        Objects.requireNonNull(unitPrice, "unitPrice is required");
    }

    public Money totalPrice() {
        return new Money(unitPrice.amount().multiply(java.math.BigDecimal.valueOf(quantity)), unitPrice.currency());
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
