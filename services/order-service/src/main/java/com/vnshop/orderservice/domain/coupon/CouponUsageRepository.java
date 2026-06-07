package com.vnshop.orderservice.domain.coupon;

public interface CouponUsageRepository {
    int getUsageCount(CouponId couponId, String buyerId);
}
