package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.SellerProfile;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;

import java.util.Objects;

public class ApproveSellerUseCase {
    private final UserRepositoryPort userRepositoryPort;

    public ApproveSellerUseCase(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = Objects.requireNonNull(userRepositoryPort, "userRepositoryPort is required");
    }

    public SellerProfile approve(String sellerId) {
        SellerProfile sellerProfile = userRepositoryPort.findSellerById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException("seller profile not found"));
        sellerProfile.approve();
        return userRepositoryPort.updateSeller(sellerProfile);
    }
}
