package com.vnshop.couponservice.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "coupon_svc", name = "coupons")
@Getter
@Setter
public class CouponJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String discountType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minOrderValue;

    @Column(precision = 19, scale = 2)
    private BigDecimal maxDiscount;

    @Column(nullable = false)
    private int maxUses;

    @Column(nullable = false)
    private int currentUses = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant validFrom;

    @Column(nullable = false)
    private Instant validUntil;
}
