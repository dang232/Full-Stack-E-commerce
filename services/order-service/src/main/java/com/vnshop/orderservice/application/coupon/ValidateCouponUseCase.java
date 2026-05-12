package com.vnshop.orderservice.application.coupon;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponException;
import com.vnshop.orderservice.domain.coupon.CouponValidator;
import java.math.BigDecimal;

public class ValidateCouponUseCase {
    private final CouponValidator couponValidator;

    public ValidateCouponUseCase(CouponValidator couponValidator) {
        this.couponValidator = couponValidator;
    }

    public ValidateCouponResponse validate(ValidateCouponCommand command) {
        CouponValidator.ValidationResult result = couponValidator.validate(command.code(), new Money(command.orderTotal()), command.userId());
        Coupon coupon = result.coupon();
        if (!result.valid()) {
            throw new CouponException(result.errorCode(), result.reason());
        }
        return new ValidateCouponResponse(
                true,
                coupon.id().value(),
                coupon.code(),
                coupon.type().name(),
                coupon.value(),
                result.discount().amount(),
                null
        );
    }

    public ValidateCouponResponse inspect(ValidateCouponCommand command) {
        CouponValidator.ValidationResult result = couponValidator.validate(command.code(), new Money(command.orderTotal()), command.userId());
        Coupon coupon = result.coupon();
        BigDecimal discount = result.discount() == null ? BigDecimal.ZERO : result.discount().amount();
        return new ValidateCouponResponse(
                result.valid(),
                coupon.id().value(),
                coupon.code(),
                coupon.type().name(),
                coupon.value(),
                discount,
                result.reason()
        );
    }
}
