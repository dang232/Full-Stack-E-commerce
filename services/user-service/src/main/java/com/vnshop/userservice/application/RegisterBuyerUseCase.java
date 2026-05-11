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

    public BuyerProfile register(RegisterBuyerCommand command) {
        BuyerProfile buyerProfile = new BuyerProfile(
                command.keycloakId(),
                command.name(),
                new PhoneNumber(command.phone()),
                command.avatarUrl(),
                List.of()
        );
        return userRepositoryPort.saveBuyer(buyerProfile);
    }
}
