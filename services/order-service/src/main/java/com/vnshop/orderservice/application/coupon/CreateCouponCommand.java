package com.vnshop.orderservice.application.coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateCouponCommand(
        String code,
        String type,
        BigDecimal value,
        BigDecimal maxDiscount,
        BigDecimal minOrderValue,
        int totalUsageLimit,
        int perUserLimit,
        LocalDateTime validFrom,
        LocalDateTime validUntil
) {
}
