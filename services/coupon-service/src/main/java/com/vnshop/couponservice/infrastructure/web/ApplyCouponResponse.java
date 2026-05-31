package com.vnshop.couponservice.infrastructure.web;

import com.vnshop.couponservice.application.ApplyCouponResult;
import java.math.BigDecimal;

public record ApplyCouponResponse(String code, BigDecimal discount, BigDecimal finalTotal) {
    public static ApplyCouponResponse from(ApplyCouponResult result) {
        return new ApplyCouponResponse(result.code(), result.discount(), result.finalTotal());
    }
}
