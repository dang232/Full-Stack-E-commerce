package com.vnshop.couponservice.domain.port.out;

/**
 * Port for tracking per-user coupon usage. The application layer uses
 * this abstraction; the infrastructure layer provides the JPA-backed
 * implementation.
 */
public interface CouponUsagePort {

    /**
     * Returns true if the given user has already used the coupon.
     */
    boolean hasUserUsedCoupon(Long couponId, String userId);

    /**
     * Records that the user consumed the coupon.
     */
    void recordUsage(Long couponId, String userId);
}
