package com.vnshop.orderservice.application.coupon;

import java.math.BigDecimal;

public record ValidateCouponCommand(String code, BigDecimal orderTotal, String userId) {
}
