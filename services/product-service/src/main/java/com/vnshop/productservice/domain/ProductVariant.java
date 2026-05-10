package com.vnshop.productservice.domain;

import java.util.Objects;

public record ProductVariant(String sku, String name, Money price, String imageUrl) {
    public ProductVariant {
        requireNonBlank(sku, "sku");
        Objects.requireNonNull(price, "price is required");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductVariant variant)) {
            return false;
        }
        return sku.equals(variant.sku);
    }

    @Override
    public int hashCode() {
        return sku.hashCode();
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
