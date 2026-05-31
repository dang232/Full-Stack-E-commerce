package com.vnshop.couponservice.application;

import com.vnshop.couponservice.domain.Coupon;
import com.vnshop.couponservice.domain.CouponValidation;
import com.vnshop.couponservice.domain.port.out.CouponRepository;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Pure read: returns whether the coupon would apply to the given order
 * amount, plus the discount it would produce. No mutation — actual usage
 * consumption happens through {@link ApplyCouponUseCase}.
 */
public class ValidateCouponUseCase {
    private final CouponRepository repository;

    public ValidateCouponUseCase(CouponRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public CouponValidation validate(String code, BigDecimal orderAmount) {
        return repository.findByCode(code)
                .map(coupon -> coupon.validate(orderAmount))
                .orElseGet(() -> CouponValidation.rejected("Coupon not found"));
    }
}
