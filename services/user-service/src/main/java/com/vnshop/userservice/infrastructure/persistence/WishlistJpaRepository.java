package com.vnshop.userservice.infrastructure.persistence;

import com.vnshop.userservice.domain.WishlistItem;
import com.vnshop.userservice.domain.port.out.WishlistRepositoryPort;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * EntityManager is constructor-injected with {@link Lazy} so the bean can be
 * instantiated in test contexts that exclude JPA autoconfiguration. The real
 * persistence calls still resolve the EntityManager from the application
 * context at first use.
 */
@Repository
public class WishlistJpaRepository implements WishlistRepositoryPort {
    private final EntityManager entityManager;

    public WishlistJpaRepository(@Lazy EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<WishlistItem> findByKeycloakId(String keycloakId) {
        return entityManager
                .createQuery(
                        "select w from WishlistItemJpaEntity w where w.id.keycloakId = :keycloakId order by w.createdAt desc",
                        WishlistItemJpaEntity.class)
                .setParameter("keycloakId", keycloakId)
                .getResultStream()
                .map(entity -> new WishlistItem(
                        entity.getId().getKeycloakId(),
                        entity.getId().getProductId(),
                        entity.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public boolean add(WishlistItem item) {
        if (exists(item.keycloakId(), item.productId())) {
            return false;
        }
        entityManager.persist(new WishlistItemJpaEntity(
                item.keycloakId(), item.productId(), item.createdAt()));
        return true;
    }

    @Override
    @Transactional
    public boolean remove(String keycloakId, String productId) {
        int deleted = entityManager
                .createQuery(
                        "delete from WishlistItemJpaEntity w where w.id.keycloakId = :keycloakId and w.id.productId = :productId")
                .setParameter("keycloakId", keycloakId)
                .setParameter("productId", productId)
                .executeUpdate();
        return deleted > 0;
    }

    @Override
    @Transactional
    public int clear(String keycloakId) {
        return entityManager
                .createQuery("delete from WishlistItemJpaEntity w where w.id.keycloakId = :keycloakId")
                .setParameter("keycloakId", keycloakId)
                .executeUpdate();
    }

    @Override
    public boolean exists(String keycloakId, String productId) {
        Long count = entityManager
                .createQuery(
                        "select count(w) from WishlistItemJpaEntity w where w.id.keycloakId = :keycloakId and w.id.productId = :productId",
                        Long.class)
                .setParameter("keycloakId", keycloakId)
                .setParameter("productId", productId)
                .getSingleResult();
        return count != null && count > 0;
    }

    @Override
    public int countByKeycloakId(String keycloakId) {
        Long count = entityManager
                .createQuery(
                        "select count(w) from WishlistItemJpaEntity w where w.id.keycloakId = :keycloakId",
                        Long.class)
                .setParameter("keycloakId", keycloakId)
                .getSingleResult();
        return count == null ? 0 : count.intValue();
    }
}
