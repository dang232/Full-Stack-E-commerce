package com.vnshop.orderservice.application.coupon;

import com.vnshop.orderservice.domain.Money;

public record ApplyCouponResponse(Money newTotal, Money discount, CouponResponse coupon) {
}
