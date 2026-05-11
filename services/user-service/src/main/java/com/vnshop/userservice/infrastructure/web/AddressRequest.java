package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.domain.Address;

public record AddressRequest(String street, String ward, String district, String city, boolean isDefault) {
    Address toDomain() {
        return new Address(street, ward, district, city, isDefault);
    }
}
