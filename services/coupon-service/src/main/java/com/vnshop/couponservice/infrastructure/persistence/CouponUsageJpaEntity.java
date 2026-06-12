package com.vnshop.couponservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "coupon_svc", name = "coupon_usages")
@Getter
@Setter
@NoArgsConstructor
public class CouponUsageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    public CouponUsageJpaEntity(Long couponId, String userId) {
        this.couponId = couponId;
        this.userId = userId;
        this.usedAt = Instant.now();
    }
}
