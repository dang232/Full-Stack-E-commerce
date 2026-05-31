package com.vnshop.couponservice.application;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Command to create or replace a coupon's editable terms. Both
 * {@link com.vnshop.couponservice.application.IssueCouponUseCase} and
 * {@link com.vnshop.couponservice.application.UpdateCouponUseCase} accept this
 * shape; on issue, {@code validFrom} is set to {@code now} by the aggregate
 * factory.
 */
public record CouponTermsCommand(
        String code,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minOrderValue,
        BigDecimal maxDiscount,
        int maxUses,
        Instant validUntil) {
}
