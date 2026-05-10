package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.SellerProfile;
import com.vnshop.userservice.domain.Tier;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;

import java.util.Objects;

public class RegisterSellerUseCase {
    private final UserRepositoryPort userRepositoryPort;

    public RegisterSellerUseCase(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = Objects.requireNonNull(userRepositoryPort, "userRepositoryPort is required");
    }

    public SellerProfile register(String keycloakId, String shopName, String bankName, String bankAccount) {
        SellerProfile sellerProfile = new SellerProfile(
                keycloakId,
                shopName,
                bankName,
                bankAccount,
                null,
                false,
                Tier.STANDARD,
                false
        );
        return userRepositoryPort.saveSeller(sellerProfile);
    }
}
