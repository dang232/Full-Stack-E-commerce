package com.vnshop.orderservice.application.coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        String code,
        String type,
        BigDecimal value,
        String description,
        BigDecimal minOrderValue,
        LocalDateTime validUntil,
        int remainingUses
) {
}
