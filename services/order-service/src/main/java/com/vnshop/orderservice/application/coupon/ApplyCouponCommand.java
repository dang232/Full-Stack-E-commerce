package com.vnshop.orderservice.application.coupon;

import java.math.BigDecimal;
import java.util.UUID;

public record ApplyCouponCommand(String code, UUID orderId, String userId, BigDecimal orderTotal) {
}
