package com.vnshop.orderservice.domain.coupon;

import com.vnshop.orderservice.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

public class Coupon {
    private final CouponId id;
    private final String code;
    private final DiscountType type;
    private final BigDecimal value;
    private final Money maxDiscount;
    private final Money minOrderValue;
    private final int totalUsageLimit;
    private int totalUsed;
    private final int perUserLimit;
    private final LocalDateTime validFrom;
    private final LocalDateTime validUntil;
    private boolean active;
    private final LocalDateTime createdAt;

    private Coupon(
            CouponId id,
            String code,
            DiscountType type,
            BigDecimal value,
            Money maxDiscount,
            Money minOrderValue,
            int totalUsageLimit,
            int totalUsed,
            int perUserLimit,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            boolean active,
            LocalDateTime createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.code = normalizeCode(code);
        this.type = Objects.requireNonNull(type, "type is required");
        this.value = Objects.requireNonNull(value, "value is required");
        this.maxDiscount = maxDiscount;
        this.minOrderValue = Objects.requireNonNull(minOrderValue, "minOrderValue is required");
        this.totalUsageLimit = totalUsageLimit;
        this.totalUsed = totalUsed;
        this.perUserLimit = perUserLimit;
        this.validFrom = Objects.requireNonNull(validFrom, "validFrom is required");
        this.validUntil = Objects.requireNonNull(validUntil, "validUntil is required");
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        validateShape();
    }

    public static Coupon create(
            CouponId id,
            String code,
            DiscountType type,
            BigDecimal value,
            Money maxDiscount,
            Money minOrderValue,
            int totalUsageLimit,
            int perUserLimit,
            LocalDateTime validFrom,
            LocalDateTime validUntil
    ) {
        return new Coupon(id, code, type, value, maxDiscount, minOrderValue, totalUsageLimit, 0, perUserLimit, validFrom, validUntil, true, LocalDateTime.now());
    }

    public static Coupon restore(
            CouponId id,
            String code,
            DiscountType type,
            BigDecimal value,
            Money maxDiscount,
            Money minOrderValue,
            int totalUsageLimit,
            int totalUsed,
            int perUserLimit,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            boolean active,
            LocalDateTime createdAt
    ) {
        return new Coupon(id, code, type, value, maxDiscount, minOrderValue, totalUsageLimit, totalUsed, perUserLimit, validFrom, validUntil, active, createdAt);
    }

    public static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("coupon code is required");
        }
        return code.trim().toUpperCase().replaceAll("\\s+", "");
    }

    public boolean isValid(Money orderTotal, int userUsageCount, LocalDateTime now) {
        return invalidCode(orderTotal, userUsageCount, now) == null;
    }

    public String invalidCode(Money orderTotal, int userUsageCount, LocalDateTime now) {
        if (!active) {
            return "COUPON_INACTIVE";
        }
        if (now.isBefore(validFrom) || now.isAfter(validUntil)) {
            return "COUPON_EXPIRED";
        }
        if (totalUsed >= totalUsageLimit) {
            return "COUPON_EXHAUSTED";
        }
        if (userUsageCount >= perUserLimit) {
            return "COUPON_USER_LIMIT";
        }
        if (orderTotal.amount().compareTo(minOrderValue.amount()) < 0) {
            return "COUPON_MIN_ORDER";
        }
        return null;
    }

    public Money calculateDiscount(Money orderTotal) {
        if (type == DiscountType.FREE_SHIPPING) {
            return Money.ZERO;
        }
        if (type == DiscountType.PERCENTAGE) {
            BigDecimal factor = value.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            Money discount = new Money(orderTotal.amount().multiply(factor).setScale(0, RoundingMode.FLOOR), orderTotal.currency());
            if (maxDiscount != null && discount.amount().compareTo(maxDiscount.amount()) > 0) {
                return maxDiscount;
            }
            return discount;
        }
        Money discount = new Money(value.setScale(0, RoundingMode.UNNECESSARY), orderTotal.currency());
        if (discount.amount().compareTo(orderTotal.amount()) > 0) {
            return orderTotal;
        }
        return discount;
    }

    public void recordUsage() {
        if (totalUsed >= totalUsageLimit) {
            throw new CouponException("COUPON_EXHAUSTED", "coupon usage limit reached");
        }
        totalUsed++;
    }

    public void releaseUsage() {
        if (totalUsed > 0) {
            totalUsed--;
        }
    }

    public void deactivate() {
        active = false;
    }

    public void update(
            DiscountType type,
            BigDecimal value,
            Money maxDiscount,
            Money minOrderValue,
            int totalUsageLimit,
            int perUserLimit,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            boolean active
    ) {
        Coupon updated = restore(id, code, type, value, maxDiscount, minOrderValue, totalUsageLimit, totalUsed, perUserLimit, validFrom, validUntil, active, createdAt);
        this.active = updated.active;
    }

    private void validateShape() {
        if (type == DiscountType.PERCENTAGE && (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("percentage discount must be between 1 and 100");
        }
        if (type == DiscountType.FIXED && value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fixed discount must be positive");
        }
        if (type == DiscountType.FREE_SHIPPING && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("free shipping discount value cannot be negative");
        }
        if (totalUsageLimit < 1) {
            throw new IllegalArgumentException("totalUsageLimit must be positive");
        }
        if (perUserLimit < 1) {
            throw new IllegalArgumentException("perUserLimit must be positive");
        }
        if (validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
    }

    public Coupon withUpdatedDetails(
            DiscountType type,
            BigDecimal value,
            Money maxDiscount,
            Money minOrderValue,
            int totalUsageLimit,
            int perUserLimit,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            boolean active
    ) {
        return restore(id, code, type, value, maxDiscount, minOrderValue, totalUsageLimit, totalUsed, perUserLimit, validFrom, validUntil, active, createdAt);
    }

    public CouponId id() {
        return id;
    }

    public String code() {
        return code;
    }

    public DiscountType type() {
        return type;
    }

    public BigDecimal value() {
        return value;
    }

    public Money maxDiscount() {
        return maxDiscount;
    }

    public Money minOrderValue() {
        return minOrderValue;
    }

    public int totalUsageLimit() {
        return totalUsageLimit;
    }

    public int totalUsed() {
        return totalUsed;
    }

    public int perUserLimit() {
        return perUserLimit;
    }

    public LocalDateTime validFrom() {
        return validFrom;
    }

    public LocalDateTime validUntil() {
        return validUntil;
    }

    public boolean active() {
        return active;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
