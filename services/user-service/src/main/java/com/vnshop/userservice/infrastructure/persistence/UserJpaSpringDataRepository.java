package com.vnshop.userservice.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface UserJpaSpringDataRepository extends JpaRepository<BuyerProfileJpaEntity, Long> {
    Optional<BuyerProfileJpaEntity> findByKeycloakId(String keycloakId);

    @Query("select seller from SellerProfileJpaEntity seller where seller.keycloakId = :sellerId")
    Optional<SellerProfileJpaEntity> findSellerEntityById(String sellerId);

    @Query("select seller from SellerProfileJpaEntity seller where seller.approved = false")
    List<SellerProfileJpaEntity> findPendingSellerEntities();
}
