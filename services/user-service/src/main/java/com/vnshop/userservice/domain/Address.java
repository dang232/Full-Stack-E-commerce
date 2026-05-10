package com.vnshop.userservice.domain;

public record Address(
        String street,
        String ward,
        String district,
        String city,
        boolean isDefault
) {
    public Address {
        requireNonBlank(street, "street");
        requireNonBlank(district, "district");
        requireNonBlank(city, "city");
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
