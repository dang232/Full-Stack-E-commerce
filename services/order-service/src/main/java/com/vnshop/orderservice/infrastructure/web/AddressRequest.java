package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.Address;
import jakarta.validation.constraints.NotBlank;

public record AddressRequest(
        @NotBlank String street,
        String ward,
        @NotBlank String district,
        @NotBlank String city) {

    Address toDomain() {
        return new Address(street, ward, district, city);
    }
}
