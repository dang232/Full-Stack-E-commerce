package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.domain.SellerProfile;

public record SellerProfileResponse(String id, String shopName, String bankName, String bankAccount, boolean approved, String tier, boolean vacationMode) {
    static SellerProfileResponse fromDomain(SellerProfile sellerProfile) {
        return new SellerProfileResponse(
                sellerProfile.id(),
                sellerProfile.shopName(),
                sellerProfile.bankName(),
                sellerProfile.bankAccount(),
                sellerProfile.approved(),
                sellerProfile.tier().name(),
                sellerProfile.vacationMode()
        );
    }
}
