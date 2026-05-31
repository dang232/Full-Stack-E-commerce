package com.vnshop.couponservice.infrastructure.web;

import java.math.BigDecimal;

public record ValidateCouponRequest(String code, BigDecimal orderAmount, BigDecimal orderTotal, String userId) {
    /** Buyer FE may send either name; treat them as aliases. */
    public BigDecimal effectiveOrderAmount() {
        return orderAmount != null ? orderAmount : orderTotal;
    }
}
