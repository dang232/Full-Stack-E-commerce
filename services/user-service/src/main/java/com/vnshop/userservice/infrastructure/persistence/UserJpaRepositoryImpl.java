package com.vnshop.userservice.infrastructure.persistence;

import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.SellerProfile;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

public class UserJpaRepositoryImpl implements UserJpaRepositoryCustom {
    private final EntityManager entityManager;

    public UserJpaRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public BuyerProfile saveBuyer(BuyerProfile buyerProfile) {
        BuyerProfileJpaEntity entity = BuyerProfileJpaEntity.fromDomain(buyerProfile);
        findBuyerEntityByKeycloakId(buyerProfile.keycloakId()).ifPresent(existing -> entity.setId(existing.getId()));
        return entityManager.merge(entity).toDomain();
    }

    public Optional<BuyerProfile> findBuyerByKeycloakId(String keycloakId) {
        return findBuyerEntityByKeycloakId(keycloakId).map(BuyerProfileJpaEntity::toDomain);
    }

    @Transactional
    public SellerProfile saveSeller(SellerProfile sellerProfile) {
        SellerProfileJpaEntity entity = SellerProfileJpaEntity.fromDomain(sellerProfile);
        findSellerEntityByKeycloakId(sellerProfile.id()).ifPresent(existing -> entity.setId(existing.getId()));
        return entityManager.merge(entity).toDomain();
    }

    public Optional<SellerProfile> findSellerById(String sellerId) {
        return findSellerEntityByKeycloakId(sellerId).map(SellerProfileJpaEntity::toDomain);
    }

    public List<SellerProfile> findPendingSellers() {
        return entityManager.createQuery(
                        "select seller from SellerProfileJpaEntity seller where seller.approved = false",
                        SellerProfileJpaEntity.class
                )
                .getResultList()
                .stream()
                .map(SellerProfileJpaEntity::toDomain)
                .toList();
    }

    @Transactional
    public SellerProfile updateSeller(SellerProfile sellerProfile) {
        return saveSeller(sellerProfile);
    }

    private Optional<BuyerProfileJpaEntity> findBuyerEntityByKeycloakId(String keycloakId) {
        return entityManager.createQuery(
                        "select buyer from BuyerProfileJpaEntity buyer left join fetch buyer.addresses where buyer.keycloakId = :keycloakId",
                        BuyerProfileJpaEntity.class
                )
                .setParameter("keycloakId", keycloakId)
                .getResultStream()
                .findFirst();
    }

    private Optional<SellerProfileJpaEntity> findSellerEntityByKeycloakId(String keycloakId) {
        return entityManager.createQuery(
                        "select seller from SellerProfileJpaEntity seller where seller.keycloakId = :keycloakId",
                        SellerProfileJpaEntity.class
                )
                .setParameter("keycloakId", keycloakId)
                .getResultStream()
                .findFirst();
    }
}
