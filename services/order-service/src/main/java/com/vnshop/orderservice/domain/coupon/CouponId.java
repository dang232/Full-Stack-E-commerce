package com.vnshop.orderservice.domain.coupon;

import java.util.UUID;

public record CouponId(UUID value) {
    public CouponId {
        if (value == null) {
            throw new IllegalArgumentException("coupon id is required");
        }
    }

    public static CouponId generate() {
        return new CouponId(UUID.randomUUID());
    }
}
