package com.vnshop.userservice.domain.port.out;

import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.SellerProfile;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {
    BuyerProfile saveBuyer(BuyerProfile buyerProfile);

    Optional<BuyerProfile> findBuyerByKeycloakId(String keycloakId);

    SellerProfile saveSeller(SellerProfile sellerProfile);

    Optional<SellerProfile> findSellerById(String sellerId);

    List<SellerProfile> findPendingSellers();

    SellerProfile updateSeller(SellerProfile sellerProfile);
}
