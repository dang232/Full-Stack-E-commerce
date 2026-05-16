package com.vnshop.userservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "wishlist_items", schema = "user_svc")
@Getter
@Setter
public class WishlistItemJpaEntity {
    @EmbeddedId
    private WishlistItemId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WishlistItemJpaEntity() {
    }

    public WishlistItemJpaEntity(String keycloakId, String productId, Instant createdAt) {
        this.id = new WishlistItemId(keycloakId, productId);
        this.createdAt = createdAt;
    }

    @Embeddable
    @Getter
    @Setter
    public static class WishlistItemId implements Serializable {
        @Column(name = "keycloak_id", nullable = false, length = 255)
        private String keycloakId;

        @Column(name = "product_id", nullable = false, length = 255)
        private String productId;

        protected WishlistItemId() {
        }

        public WishlistItemId(String keycloakId, String productId) {
            this.keycloakId = keycloakId;
            this.productId = productId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WishlistItemId other)) return false;
            return Objects.equals(keycloakId, other.keycloakId)
                    && Objects.equals(productId, other.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(keycloakId, productId);
        }
    }
}
