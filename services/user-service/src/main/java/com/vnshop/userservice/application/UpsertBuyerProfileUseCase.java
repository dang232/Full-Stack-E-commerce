package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.PhoneNumber;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;

public class UpsertBuyerProfileUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final RegisterBuyerUseCase registerBuyerUseCase;

    public UpsertBuyerProfileUseCase(UserRepositoryPort userRepositoryPort, RegisterBuyerUseCase registerBuyerUseCase) {
        this.userRepositoryPort = userRepositoryPort;
        this.registerBuyerUseCase = registerBuyerUseCase;
    }

    public BuyerProfile upsert(UpsertBuyerProfileCommand command) {
        return userRepositoryPort.findBuyerByKeycloakId(command.keycloakId())
                .map(existing -> {
                    existing.updateProfile(command.name(), new PhoneNumber(command.phone()), command.avatarUrl());
                    return userRepositoryPort.saveBuyer(existing);
                })
                .orElseGet(() -> registerBuyerUseCase.register(new RegisterBuyerCommand(command.keycloakId(), command.name(), command.phone(), command.avatarUrl())));
    }
}
