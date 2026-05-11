package com.vnshop.orderservice.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardSummary(
        long totalOrders,
        BigDecimal totalRevenue,
        long activeBuyers,
        long activeSellers,
        BigDecimal avgOrderValue,
        LocalDate periodStart,
        LocalDate periodEnd
) {
}
