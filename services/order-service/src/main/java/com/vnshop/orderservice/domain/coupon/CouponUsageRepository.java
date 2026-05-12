package com.vnshop.orderservice.domain.coupon;

import java.util.Optional;
import java.util.UUID;

public interface CouponUsageRepository {
    int getUsageCount(CouponId couponId, String userId);

    void recordUsage(CouponId couponId, String userId);

    void recordUsage(CouponId couponId, String userId, UUID orderId);

    void releaseUsage(CouponId couponId, String userId);

    Optional<CouponId> releaseUsageForOrder(UUID orderId, String userId);
}
