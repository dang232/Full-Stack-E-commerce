package com.vnshop.orderservice.domain.projection;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryProjection(
    String orderId,
    String buyerId,
    String sellerId,
    String status,
    BigDecimal totalAmount,
    int itemCount,
    Instant createdAt,
    Instant updatedAt
) {}
