package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;

import java.util.List;
import java.util.Objects;

/**
 * Resolves a batch of buyer keycloakIds to their public-facing display
 * info (name + avatar). Used by other services that need to render a
 * buyer name next to a UUID — e.g. product-service rendering reviewer
 * names on the public Reviews tab.
 *
 * <p>Returns only the buyers that exist; missing ids are silently
 * dropped. Callers that need a per-id status should infer "missing"
 * from the result list size vs the input.</p>
 */
public class ListBuyerPublicProfilesUseCase {
    private final UserRepositoryPort userRepositoryPort;

    public ListBuyerPublicProfilesUseCase(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = Objects.requireNonNull(userRepositoryPort, "userRepositoryPort is required");
    }

    public List<BuyerProfile> list(List<String> keycloakIds) {
        if (keycloakIds == null || keycloakIds.isEmpty()) {
            return List.of();
        }
        return userRepositoryPort.findBuyersByKeycloakIds(keycloakIds);
    }
}
