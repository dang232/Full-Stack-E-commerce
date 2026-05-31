package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.ListBuyerPublicProfilesUseCase;
import com.vnshop.userservice.domain.BuyerProfile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public-profile lookup endpoint. Used by other services (e.g.
 * product-service rendering reviewer names on the Reviews tab) to
 * resolve a batch of buyer keycloakIds into display info without
 * leaking authenticated-only fields like phone or addresses.
 *
 * <p>Returns only the buyers that exist; missing ids are silently
 * dropped. Cache-friendly: the response is keyed off the sorted
 * id list. No JWT required since this only exposes the same fields
 * that already render publicly on the storefront (name, avatar).</p>
 */
@RestController
@RequestMapping("/users/public-profiles")
public class UserPublicProfileController {
    private static final int MAX_BATCH = 100;

    private final ListBuyerPublicProfilesUseCase listBuyerPublicProfilesUseCase;

    public UserPublicProfileController(ListBuyerPublicProfilesUseCase listBuyerPublicProfilesUseCase) {
        this.listBuyerPublicProfilesUseCase = listBuyerPublicProfilesUseCase;
    }

    @GetMapping
    public ApiResponse<List<PublicProfileResponse>> list(@RequestParam(value = "ids", required = false) List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        // Cap the batch size so a malicious client can't fan out an
        // unbounded query against the BuyerProfileJpa repo.
        List<String> bounded = ids.size() > MAX_BATCH ? ids.subList(0, MAX_BATCH) : ids;
        List<BuyerProfile> profiles = listBuyerPublicProfilesUseCase.list(bounded);
        return ApiResponse.ok(profiles.stream().map(PublicProfileResponse::fromDomain).toList());
    }

    public record PublicProfileResponse(String userId, String displayName, String avatarUrl) {
        static PublicProfileResponse fromDomain(BuyerProfile profile) {
            return new PublicProfileResponse(profile.keycloakId(), profile.name(), profile.avatarUrl());
        }
    }
}
