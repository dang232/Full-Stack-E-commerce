package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import com.vnshop.userservice.infrastructure.keycloak.KeycloakAdminClient;

import java.util.List;
import java.util.Objects;

public class AdminUserUseCase {

    private final UserRepositoryPort userRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    public AdminUserUseCase(UserRepositoryPort userRepository, KeycloakAdminClient keycloakAdminClient) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository is required");
        this.keycloakAdminClient = Objects.requireNonNull(keycloakAdminClient, "keycloakAdminClient is required");
    }

    public List<BuyerProfile> searchUsers(String email, String phone) {
        return userRepository.searchBuyers(email, phone);
    }

    public BuyerProfile banUser(String keycloakId) {
        BuyerProfile profile = userRepository.findBuyerByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + keycloakId));
        profile.ban();
        keycloakAdminClient.disableUser(profile.keycloakId());
        return userRepository.saveBuyer(profile);
    }

    public BuyerProfile unbanUser(String keycloakId) {
        BuyerProfile profile = userRepository.findBuyerByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + keycloakId));
        profile.unban();
        keycloakAdminClient.enableUser(profile.keycloakId());
        return userRepository.saveBuyer(profile);
    }
}
