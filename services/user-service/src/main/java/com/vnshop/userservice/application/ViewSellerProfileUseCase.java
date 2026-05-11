package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.SellerProfile;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;

public class ViewSellerProfileUseCase {

    private final UserRepositoryPort userRepositoryPort;

    public ViewSellerProfileUseCase(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }

    public SellerProfile view(String sellerId) {
        return userRepositoryPort.findSellerById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException("seller profile not found"));
    }
}
