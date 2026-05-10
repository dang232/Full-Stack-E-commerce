package com.vnshop.userservice.infrastructure.persistence;

import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserJpaRepository extends JpaRepository<BuyerProfileJpaEntity, Long>, UserRepositoryPort, UserJpaRepositoryCustom {
    Optional<BuyerProfileJpaEntity> findByKeycloakId(String keycloakId);

    @Query("select seller from SellerProfileJpaEntity seller where seller.keycloakId = :sellerId")
    Optional<SellerProfileJpaEntity> findSellerEntityById(String sellerId);

    @Query("select seller from SellerProfileJpaEntity seller where seller.approved = false")
    List<SellerProfileJpaEntity> findPendingSellerEntities();
}
