package com.vnshop.orderservice.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Per-seller daily revenue + order count. {@code revenue} is the gross
 * line-item total for sub-orders the seller fulfilled on {@code date}.
 */
public record SellerRevenuePoint(LocalDate date, BigDecimal revenue, long orderCount) {
}
