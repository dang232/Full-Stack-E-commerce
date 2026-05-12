package com.vnshop.orderservice.infrastructure.persistence.coupon;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponId;
import com.vnshop.orderservice.domain.coupon.DiscountType;
import com.vnshop.orderservice.infrastructure.persistence.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "order_svc", name = "coupons")
@Getter
@Setter
public class CouponJpaEntity extends BaseJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType type;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal value;

    @Column(precision = 19, scale = 0)
    private BigDecimal maxDiscountAmount;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal minOrderValueAmount;

    @Column(nullable = false)
    private int totalUsageLimit;

    @Column(nullable = false)
    private int totalUsed;

    @Column(nullable = false)
    private int perUserLimit;

    @Column(nullable = false)
    private LocalDateTime validFrom;

    @Column(nullable = false)
    private LocalDateTime validUntil;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "coupon_created_at", nullable = false)
    private LocalDateTime couponCreatedAt;

    public static CouponJpaEntity fromDomain(Coupon coupon) {
        CouponJpaEntity entity = new CouponJpaEntity();
        entity.id = coupon.id().value();
        entity.code = coupon.code();
        entity.type = coupon.type();
        entity.value = coupon.value();
        entity.maxDiscountAmount = coupon.maxDiscount() == null ? null : coupon.maxDiscount().amount();
        entity.minOrderValueAmount = coupon.minOrderValue().amount();
        entity.totalUsageLimit = coupon.totalUsageLimit();
        entity.totalUsed = coupon.totalUsed();
        entity.perUserLimit = coupon.perUserLimit();
        entity.validFrom = coupon.validFrom();
        entity.validUntil = coupon.validUntil();
        entity.active = coupon.active();
        entity.couponCreatedAt = coupon.createdAt();
        return entity;
    }

    public Coupon toDomain() {
        return Coupon.restore(
                new CouponId(id),
                code,
                type,
                value,
                maxDiscountAmount == null ? null : new Money(maxDiscountAmount),
                new Money(minOrderValueAmount),
                totalUsageLimit,
                totalUsed,
                perUserLimit,
                validFrom,
                validUntil,
                active,
                couponCreatedAt
        );
    }
}
