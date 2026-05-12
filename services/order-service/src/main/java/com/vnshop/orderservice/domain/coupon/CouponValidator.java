package com.vnshop.orderservice.domain.coupon;

import com.vnshop.orderservice.domain.Money;
import java.time.Clock;
import java.time.LocalDateTime;

public class CouponValidator {
    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;
    private final Clock clock;

    public CouponValidator(CouponRepository couponRepository, CouponUsageRepository usageRepository, Clock clock) {
        this.couponRepository = couponRepository;
        this.usageRepository = usageRepository;
        this.clock = clock;
    }

    public ValidationResult validate(String code, Money orderTotal, String userId) {
        Coupon coupon = couponRepository.findByCode(Coupon.normalizeCode(code))
                .orElseThrow(() -> new CouponException("COUPON_NOT_FOUND", "coupon not found"));
        int userUsage = usageRepository.getUsageCount(coupon.id(), userId);
        String invalidCode = coupon.invalidCode(orderTotal, userUsage, LocalDateTime.now(clock));
        if (invalidCode != null) {
            return ValidationResult.invalid(coupon, invalidCode, reason(invalidCode));
        }
        return ValidationResult.valid(coupon, coupon.calculateDiscount(orderTotal));
    }

    private static String reason(String code) {
        return switch (code) {
            case "COUPON_EXPIRED" -> "Coupon expired";
            case "COUPON_EXHAUSTED" -> "Coupon fully redeemed";
            case "COUPON_USER_LIMIT" -> "You have used this coupon";
            case "COUPON_MIN_ORDER" -> "Minimum order value not met";
            case "COUPON_INACTIVE" -> "Coupon inactive";
            default -> "Coupon not applicable";
        };
    }

    public record ValidationResult(boolean valid, Coupon coupon, Money discount, String reason, String errorCode) {
        static ValidationResult valid(Coupon coupon, Money discount) {
            return new ValidationResult(true, coupon, discount, null, null);
        }

        static ValidationResult invalid(Coupon coupon, String errorCode, String reason) {
            return new ValidationResult(false, coupon, Money.ZERO, reason, errorCode);
        }
    }
}
