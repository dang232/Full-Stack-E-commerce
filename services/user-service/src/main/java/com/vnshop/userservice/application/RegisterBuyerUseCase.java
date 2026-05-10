package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.PhoneNumber;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;

import java.util.List;
import java.util.Objects;

public class RegisterBuyerUseCase {
    private final UserRepositoryPort userRepositoryPort;

    public RegisterBuyerUseCase(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = Objects.requireNonNull(userRepositoryPort, "userRepositoryPort is required");
    }

    public BuyerProfile register(String keycloakId, String name, String phone, String avatarUrl) {
        BuyerProfile buyerProfile = new BuyerProfile(
                keycloakId,
                name,
                new PhoneNumber(phone),
                avatarUrl,
                List.of()
        );
        return userRepositoryPort.saveBuyer(buyerProfile);
    }
}
