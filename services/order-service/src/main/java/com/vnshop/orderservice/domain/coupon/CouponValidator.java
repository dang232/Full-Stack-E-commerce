package com.vnshop.orderservice.domain.coupon;

import com.vnshop.orderservice.domain.Money;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain service that validates a coupon code against business rules.
 */
public class CouponValidator {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;
    private final Clock clock;

    public CouponValidator(CouponRepository couponRepository, CouponUsageRepository usageRepository, Clock clock) {
        this.couponRepository = Objects.requireNonNull(couponRepository, "couponRepository is required");
        this.usageRepository = Objects.requireNonNull(usageRepository, "usageRepository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /**
     * Validates a coupon code for a given order total and buyer.
     * Normalises the code (trim + upper-case) before lookup.
     *
     * @throws CouponException with code COUPON_NOT_FOUND if the coupon does not exist
     */
    public ValidationResult validate(String rawCode, Money orderTotal, String buyerId) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase().replaceAll("\\s+", "");
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new CouponException("COUPON_NOT_FOUND", "Coupon not found: " + code));

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        int userUsage = usageRepository.getUsageCount(coupon.id(), buyerId);
        String errorCode = coupon.invalidCode(orderTotal, userUsage, now);

        if (errorCode != null) {
            return ValidationResult.invalid(errorCode);
        }
        Money discount = coupon.calculateDiscount(orderTotal);
        return ValidationResult.valid(discount);
    }

    public record ValidationResult(boolean valid, Money discount, String errorCode) {

        public static ValidationResult valid(Money discount) {
            return new ValidationResult(true, discount, null);
        }

        public static ValidationResult invalid(String errorCode) {
            return new ValidationResult(false, Money.ZERO, errorCode);
        }
    }
}
