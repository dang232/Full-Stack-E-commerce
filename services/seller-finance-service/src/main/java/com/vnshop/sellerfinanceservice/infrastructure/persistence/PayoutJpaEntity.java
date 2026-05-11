package com.vnshop.sellerfinanceservice.infrastructure.persistence;

import com.vnshop.sellerfinanceservice.infrastructure.persistence.BaseJpaEntity;
import com.vnshop.sellerfinanceservice.domain.Payout;
import com.vnshop.sellerfinanceservice.domain.PayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "seller_finance_svc", name = "payouts")
@Getter
@Setter
public class PayoutJpaEntity extends BaseJpaEntity {
    @Id
    @Column(name = "payout_id", nullable = false, columnDefinition = "uuid")
    private UUID payoutId;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayoutStatus status;


    protected PayoutJpaEntity() {
    }

    static PayoutJpaEntity fromDomain(Payout payout) {
        PayoutJpaEntity entity = new PayoutJpaEntity();
        entity.payoutId = payout.payoutId();
        entity.sellerId = payout.sellerId();
        entity.amount = payout.amount();
        entity.status = payout.status();
        return entity;
    }

    Payout toDomain() {
        return new Payout(payoutId, sellerId, amount, status, getCreatedAt());
    }
}
