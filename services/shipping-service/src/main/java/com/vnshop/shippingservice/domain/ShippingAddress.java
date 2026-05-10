package com.vnshop.shippingservice.domain;

public record ShippingAddress(
        String fullName,
        String phone,
        String addressLine,
        String wardCode,
        String districtCode,
        String provinceCode
) {
}
