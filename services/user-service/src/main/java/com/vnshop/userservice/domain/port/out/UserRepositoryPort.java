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

    SellerProfile saveSeller(SellerProfile sellerProfile);

    Optional<SellerProfile> findSellerById(String sellerId);

    List<SellerProfile> findPendingSellers();

    SellerProfile updateSeller(SellerProfile sellerProfile);

    List<SellerProfile> findApprovedSellers(int page, int size);

    long countApprovedSellers();
}
