package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.domain.Address;

public record AddressResponse(String street, String ward, String district, String city, boolean isDefault) {
    static AddressResponse fromDomain(Address address) {
        return new AddressResponse(address.street(), address.ward(), address.district(), address.city(), address.isDefault());
    }
}
