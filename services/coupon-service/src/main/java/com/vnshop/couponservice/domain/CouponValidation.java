package com.vnshop.couponservice.domain;

import java.math.BigDecimal;

/**
 * Outcome of {@link Coupon#validate(BigDecimal)}. {@code valid=true} means the
 * caller may proceed to consume usage; the {@code discount} is the amount that
 * <em>would</em> apply at the given order total. The validation step never
 * mutates the coupon — actual usage decrement happens through the repository's
 * atomic {@code tryConsumeUsage}.
 */
public record CouponValidation(boolean valid, BigDecimal discount, String message) {
    public static CouponValidation ok(BigDecimal discount) {
        return new CouponValidation(true, discount, "Coupon is valid");
    }

    public static CouponValidation rejected(String message) {
        return new CouponValidation(false, BigDecimal.ZERO, message);
    }
}
