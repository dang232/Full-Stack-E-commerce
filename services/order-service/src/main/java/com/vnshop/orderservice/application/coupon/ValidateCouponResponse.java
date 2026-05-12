package com.vnshop.orderservice.application.coupon;

import java.math.BigDecimal;
import java.util.UUID;

public record ValidateCouponResponse(
        boolean valid,
        UUID couponId,
        String code,
        String type,
        BigDecimal value,
        BigDecimal discount,
        String reason
) {
}
