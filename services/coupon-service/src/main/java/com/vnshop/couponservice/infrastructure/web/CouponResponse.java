package com.vnshop.couponservice.infrastructure.web;

import com.vnshop.couponservice.domain.Coupon;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Wire-format DTO for coupon reads. Field names mirror {@link CreateCouponRequest}
 * — {@code type}/{@code value} — so admin and buyer FE code can serialise both
 * directions through the same shape.
 */
public record CouponResponse(
        Long id,
        String code,
        String type,
        BigDecimal value,
        BigDecimal minOrderValue,
        BigDecimal maxDiscount,
        int maxUses,
        int currentUses,
        boolean active,
        Instant validFrom,
        Instant validUntil) {

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.id(),
                coupon.code(),
                coupon.discountType().name(),
                coupon.discountValue(),
                coupon.minOrderValue(),
                coupon.maxDiscount(),
                coupon.maxUses(),
                coupon.currentUses(),
                coupon.active(),
                coupon.validFrom(),
                coupon.validUntil());
    }
}
