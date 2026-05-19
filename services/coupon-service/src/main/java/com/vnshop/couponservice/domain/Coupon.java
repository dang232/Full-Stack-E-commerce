package com.vnshop.couponservice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Coupon aggregate. Owns the rules that decide whether a coupon may be
 * consumed for a given order amount and what discount that produces.
 *
 * <p>Identity is the database id (null until first persisted). Mutating state
 * — {@code currentUses}, {@code active}, the editable fields — flows through
 * the explicit methods on this aggregate so the invariants live in one place
 * (e.g. {@code maxUses > 0}, {@code minOrderValue >= 0}, percentage values
 * within 0–100). The atomic usage increment used during apply is delegated to
 * the repository port — the DB enforces the {@code currentUses < maxUses}
 * predicate inside the UPDATE so concurrent applies cannot oversell.</p>
 */
public final class Coupon {
    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final Long id;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderValue;
    private BigDecimal maxDiscount;
    private int maxUses;
    private int currentUses;
    private boolean active;
    private final Instant validFrom;
    private Instant validUntil;

    public Coupon(Long id,
                  String code,
                  DiscountType discountType,
                  BigDecimal discountValue,
                  BigDecimal minOrderValue,
                  BigDecimal maxDiscount,
                  int maxUses,
                  int currentUses,
                  boolean active,
                  Instant validFrom,
                  Instant validUntil) {
        this.id = id;
        this.code = requireCode(code);
        this.discountType = Objects.requireNonNull(discountType, "discountType");
        this.discountValue = requireDiscountValue(discountType, discountValue);
        this.minOrderValue = requireNonNegative(minOrderValue, "minOrderValue");
        this.maxDiscount = (maxDiscount == null) ? null : requireNonNegative(maxDiscount, "maxDiscount");
        this.maxUses = requirePositive(maxUses, "maxUses");
        this.currentUses = requireNonNegative(currentUses, "currentUses");
        this.active = active;
        this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
        this.validUntil = Objects.requireNonNull(validUntil, "validUntil");
    }

    /** Factory for a brand-new coupon — caller has not yet picked an id. */
    public static Coupon issue(String code,
                               DiscountType discountType,
                               BigDecimal discountValue,
                               BigDecimal minOrderValue,
                               BigDecimal maxDiscount,
                               int maxUses,
                               Instant validUntil,
                               Clock clock) {
        Instant now = Instant.now(Objects.requireNonNull(clock, "clock"));
        // Past validUntil is intentionally allowed: admins occasionally need to
        // backfill historical promo codes, and tests rely on creating an
        // already-expired coupon to exercise the validate-rejects-expired path.
        // The expiry check lives on validate() / apply, not on issue.
        return new Coupon(
                null,
                code,
                discountType,
                discountValue,
                minOrderValue == null ? BigDecimal.ZERO : minOrderValue,
                maxDiscount,
                maxUses,
                0,
                true,
                now,
                validUntil);
    }

    public CouponValidation validate(BigDecimal orderAmount) {
        Objects.requireNonNull(orderAmount, "orderAmount");
        if (!active) {
            return CouponValidation.rejected("Coupon is inactive");
        }
        if (validUntil.isBefore(Instant.now())) {
            return CouponValidation.rejected("Coupon is expired");
        }
        if (orderAmount.compareTo(minOrderValue) < 0) {
            return CouponValidation.rejected("Order amount is below minimum");
        }
        if (currentUses >= maxUses) {
            return CouponValidation.rejected("Coupon usage limit exceeded");
        }
        return CouponValidation.ok(calculateDiscount(orderAmount));
    }

    public BigDecimal calculateDiscount(BigDecimal orderAmount) {
        Objects.requireNonNull(orderAmount, "orderAmount");
        BigDecimal raw = switch (discountType) {
            case PERCENTAGE -> orderAmount.multiply(discountValue)
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            case FIXED -> discountValue;
        };
        if (maxDiscount != null && raw.compareTo(maxDiscount) > 0) {
            return maxDiscount;
        }
        return raw;
    }

    public void editTerms(String code,
                          DiscountType discountType,
                          BigDecimal discountValue,
                          BigDecimal minOrderValue,
                          BigDecimal maxDiscount,
                          int maxUses,
                          Instant validUntil) {
        this.code = requireCode(code);
        this.discountType = Objects.requireNonNull(discountType, "discountType");
        this.discountValue = requireDiscountValue(discountType, discountValue);
        this.minOrderValue = minOrderValue == null
                ? BigDecimal.ZERO
                : requireNonNegative(minOrderValue, "minOrderValue");
        this.maxDiscount = (maxDiscount == null) ? null : requireNonNegative(maxDiscount, "maxDiscount");
        this.maxUses = requirePositive(maxUses, "maxUses");
        this.validUntil = Objects.requireNonNull(validUntil, "validUntil");
    }

    public void deactivate() {
        this.active = false;
    }

    public Long id() { return id; }
    public String code() { return code; }
    public DiscountType discountType() { return discountType; }
    public BigDecimal discountValue() { return discountValue; }
    public BigDecimal minOrderValue() { return minOrderValue; }
    public BigDecimal maxDiscount() { return maxDiscount; }
    public int maxUses() { return maxUses; }
    public int currentUses() { return currentUses; }
    public boolean active() { return active; }
    public Instant validFrom() { return validFrom; }
    public Instant validUntil() { return validUntil; }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return code;
    }

    private static BigDecimal requireDiscountValue(DiscountType type, BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("discount value must not be null");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("discount value must be non-negative");
        }
        if (type == DiscountType.PERCENTAGE && value.compareTo(MAX_PERCENTAGE) > 0) {
            throw new IllegalArgumentException("percentage discount must not exceed 100");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    private static int requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    private static int requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
