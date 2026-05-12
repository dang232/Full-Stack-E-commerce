package com.vnshop.orderservice.infrastructure.persistence.coupon;

import com.vnshop.orderservice.infrastructure.persistence.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "order_svc", name = "coupon_usages")
@Getter
@Setter
public class CouponUsageJpaEntity extends BaseJpaEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID couponId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private boolean active;
}
