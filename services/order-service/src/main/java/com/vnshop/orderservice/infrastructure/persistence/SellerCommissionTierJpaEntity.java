package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.CommissionTier;
import jakarta.persistence.*;

@Entity
@Table(name = "seller_commission_tier", schema = "order_svc")
public class SellerCommissionTierJpaEntity {
    @Id
    @Column(name = "seller_id")
    private String sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false)
    private CommissionTier tier;

    protected SellerCommissionTierJpaEntity() {}

    public SellerCommissionTierJpaEntity(String sellerId, CommissionTier tier) {
        this.sellerId = sellerId;
        this.tier = tier;
    }

    public String sellerId() { return sellerId; }
    public CommissionTier tier() { return tier; }
}
