package com.vnshop.orderservice.infrastructure.web;

import java.math.BigDecimal;

public record CheckoutBreakdownResponse(
        BigDecimal itemsTotal,
        BigDecimal shippingEstimate,
        BigDecimal discount,
        BigDecimal finalAmount
) {
}
