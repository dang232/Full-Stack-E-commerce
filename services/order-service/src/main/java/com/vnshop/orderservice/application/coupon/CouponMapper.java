package com.vnshop.orderservice.application.coupon;

import com.vnshop.orderservice.domain.coupon.Coupon;

final class CouponMapper {
    private CouponMapper() {
    }

    static CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.id().value(),
                coupon.code(),
                coupon.type().name(),
                coupon.value(),
                coupon.type().name() + " discount",
                coupon.minOrderValue().amount(),
                coupon.validUntil(),
                Math.max(0, coupon.totalUsageLimit() - coupon.totalUsed())
        );
    }
}
