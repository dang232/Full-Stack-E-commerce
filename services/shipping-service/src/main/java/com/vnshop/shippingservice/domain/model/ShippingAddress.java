package com.vnshop.shippingservice.domain.model;

public record ShippingAddress(
        String name,
        String phone,
        String street,
        String ward,
        String district,
        String province) {
}
