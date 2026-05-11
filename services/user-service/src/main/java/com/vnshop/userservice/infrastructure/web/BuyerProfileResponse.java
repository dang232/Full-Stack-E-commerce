package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.domain.BuyerProfile;

import java.util.List;

public record BuyerProfileResponse(String keycloakId, String name, String phone, String avatarUrl, List<AddressResponse> addresses) {
    static BuyerProfileResponse fromDomain(BuyerProfile buyerProfile) {
        return new BuyerProfileResponse(
                buyerProfile.keycloakId(),
                buyerProfile.name(),
                buyerProfile.phone() == null ? null : buyerProfile.phone().value(),
                buyerProfile.avatarUrl(),
                buyerProfile.addresses().stream().map(AddressResponse::fromDomain).toList()
        );
    }
}
