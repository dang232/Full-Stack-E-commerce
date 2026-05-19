package com.vnshop.couponservice.domain;

/**
 * Strategy for computing the discount applied to an order amount.
 *
 * <p>{@link #PERCENTAGE} multiplies the order amount by {@code value/100};
 * {@link #FIXED} subtracts {@code value} directly. The PERCENTAGE result is
 * subsequently capped by {@link Coupon#maxDiscount()} when present.</p>
 */
public enum DiscountType {
    FIXED,
    PERCENTAGE;

    public static DiscountType fromWire(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("discount type must not be null");
        }
        return DiscountType.valueOf(raw.trim().toUpperCase());
    }
}
