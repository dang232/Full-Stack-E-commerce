package com.vnshop.couponservice.infrastructure.web;

import com.vnshop.couponservice.domain.CouponValidation;
import java.math.BigDecimal;

public record ValidateCouponResponse(boolean valid, BigDecimal discount, String message) {
    public static ValidateCouponResponse from(CouponValidation v) {
        return new ValidateCouponResponse(v.valid(), v.discount(), v.message());
    }
}
