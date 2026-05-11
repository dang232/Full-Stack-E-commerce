package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.Address;

public record AddressResponse(String street, String ward, String district, String city) {

    static AddressResponse fromDomain(Address address) {
        return new AddressResponse(address.street(), address.ward(), address.district(), address.city());
    }
}
