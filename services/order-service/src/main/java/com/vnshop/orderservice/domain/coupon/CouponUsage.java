package com.vnshop.orderservice.domain.coupon;

public record CouponUsage(CouponId couponId, String userId, int usageCount) {
    public CouponUsage increment() {
        return new CouponUsage(couponId, userId, usageCount + 1);
    }

    public CouponUsage decrement() {
        return new CouponUsage(couponId, userId, Math.max(0, usageCount - 1));
    }
}
