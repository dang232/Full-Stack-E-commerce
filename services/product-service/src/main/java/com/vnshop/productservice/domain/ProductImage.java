package com.vnshop.productservice.domain;

public record ProductImage(String url, String alt, int sortOrder) {
    public ProductImage {
        requireNonBlank(url, "url");
    }

    public ProductImage withSortOrder(int sortOrder) {
        return new ProductImage(url, alt, sortOrder);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductImage image)) {
            return false;
        }
        return url.equals(image.url);
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
