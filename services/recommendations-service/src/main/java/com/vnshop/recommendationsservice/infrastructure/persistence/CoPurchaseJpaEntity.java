package com.vnshop.recommendationsservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;

/**
 * Row in the symmetric co-purchase aggregate table. Every order containing
 * products A and B contributes +1 to ({@code product_a=A,product_b=B}) and
 * +1 to ({@code product_a=B,product_b=A}). Reads filter by {@code product_a}
 * and order by {@code coCount} desc — see
 * {@link CoPurchaseRepository#findTopByProductA(String, org.springframework.data.domain.Pageable)}.
 */
@Entity
@Table(name = "co_purchases")
@Getter
@Setter
public class CoPurchaseJpaEntity {

    @EmbeddedId
    private CoPurchaseId id;

    @Column(name = "co_count", nullable = false)
    private long coCount;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public CoPurchaseJpaEntity() {
    }

    public CoPurchaseJpaEntity(String productA, String productB, long coCount, Instant lastSeenAt) {
        this.id = new CoPurchaseId(productA, productB);
        this.coCount = coCount;
        this.lastSeenAt = lastSeenAt == null ? Instant.now() : lastSeenAt;
    }

    public String productA() {
        return id == null ? null : id.getProductA();
    }

    public String productB() {
        return id == null ? null : id.getProductB();
    }

    @jakarta.persistence.Embeddable
    @Getter
    @Setter
    public static class CoPurchaseId implements Serializable {
        @Column(name = "product_a", nullable = false, length = 64)
        private String productA;

        @Column(name = "product_b", nullable = false, length = 64)
        private String productB;

        public CoPurchaseId() {
        }

        public CoPurchaseId(String productA, String productB) {
            this.productA = productA;
            this.productB = productB;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CoPurchaseId that)) return false;
            return Objects.equals(productA, that.productA) && Objects.equals(productB, that.productB);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productA, productB);
        }
    }
}
