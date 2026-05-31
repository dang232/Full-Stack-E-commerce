package com.vnshop.shippingservice.infrastructure.carrier;

final class CarrierConfig {
    private CarrierConfig() {
    }

    static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for live carrier mode");
        }
        return value;
    }
}
