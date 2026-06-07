package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;

import java.util.List;
import java.util.Objects;

public class AdminUserUseCase {

    private final UserRepositoryPort userRepository;

    public AdminUserUseCase(UserRepositoryPort userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository is required");
    }

    public List<BuyerProfile> searchUsers(String email, String phone) {
        return userRepository.searchBuyers(email, phone);
    }

    public BuyerProfile banUser(String keycloakId) {
        BuyerProfile profile = userRepository.findBuyerByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + keycloakId));
        profile.ban();
        return userRepository.saveBuyer(profile);
    }

    public BuyerProfile unbanUser(String keycloakId) {
        BuyerProfile profile = userRepository.findBuyerByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + keycloakId));
        profile.unban();
        return userRepository.saveBuyer(profile);
    }
}
