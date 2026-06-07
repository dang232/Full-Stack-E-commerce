package com.vnshop.orderservice.domain.coupon;

import java.util.Objects;
import java.util.UUID;

public record CouponId(UUID value) {

    public CouponId {
        Objects.requireNonNull(value, "value is required");
    }

    public static CouponId generate() {
        return new CouponId(UUID.randomUUID());
    }

    public static CouponId of(UUID value) {
        return new CouponId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
