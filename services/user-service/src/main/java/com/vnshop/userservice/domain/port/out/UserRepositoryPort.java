package com.vnshop.userservice.domain.port.out;

import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.SellerProfile;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {
    BuyerProfile saveBuyer(BuyerProfile buyerProfile);

    Optional<BuyerProfile> findBuyerByKeycloakId(String keycloakId);

    /**
     * Batch lookup for public profile rendering. Returns only the buyers
     * that exist; missing ids are silently dropped. Order is not
     * guaranteed; callers should index by {@link BuyerProfile#keycloakId()}.
     */
    List<BuyerProfile> findBuyersByKeycloakIds(List<String> keycloakIds);

    /**
     * Admin search by email (keycloakId prefix match) or phone substring.
     * Both params are optional; if both are null/blank, returns an empty list.
     */
    List<BuyerProfile> searchBuyers(String email, String phone);

    SellerProfile saveSeller(SellerProfile sellerProfile);

    Optional<SellerProfile> findSellerById(String sellerId);

    List<SellerProfile> findPendingSellers();

    SellerProfile updateSeller(SellerProfile sellerProfile);

    List<SellerProfile> findApprovedSellers(int page, int size);

    long countApprovedSellers();

    /**
     * Anonymizes PII fields for the buyer with the given Keycloak id.
     * Called as part of the GDPR right-to-erasure flow.
     */
    void anonymize(String keycloakId);
}
