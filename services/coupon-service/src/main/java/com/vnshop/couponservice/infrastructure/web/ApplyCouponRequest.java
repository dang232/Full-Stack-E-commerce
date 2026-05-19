package com.vnshop.couponservice.infrastructure.web;

import java.math.BigDecimal;

public record ApplyCouponRequest(
        String code,
        Long orderId,
        String userId,
        BigDecimal orderAmount,
        BigDecimal orderTotal) {

    public BigDecimal effectiveOrderAmount() {
        return orderAmount != null ? orderAmount : orderTotal;
    }
}
