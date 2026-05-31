package com.vnshop.couponservice.infrastructure.web;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Canonical create/update payload. Field names match the names FE admin's
 * {@code CouponWriteBody} sends and FE buyer code reads — {@code type}/
 * {@code value}/{@code maxUses}.
 */
public record CreateCouponRequest(
        String code,
        String type,
        BigDecimal value,
        BigDecimal minOrderValue,
        BigDecimal maxDiscount,
        int maxUses,
        Instant validUntil) {
}
