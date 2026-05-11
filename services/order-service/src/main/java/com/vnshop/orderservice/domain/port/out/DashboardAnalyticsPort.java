package com.vnshop.orderservice.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DashboardAnalyticsPort {

    long countByDateBetween(LocalDate startDate, LocalDate endDate);

    BigDecimal sumRevenueByDateBetween(LocalDate startDate, LocalDate endDate);

    long countDistinctBuyerId(LocalDate startDate, LocalDate endDate);

    long countDistinctSellerId(LocalDate startDate, LocalDate endDate);

    List<RevenueByDate> revenueByDateBetween(LocalDate startDate, LocalDate endDate);

    List<TopMetric> topProducts(int limit);

    List<TopMetric> topSellers(int limit);

    record RevenueByDate(LocalDate date, BigDecimal revenue) {}

    record TopMetric(String id, String name, BigDecimal value) {}
}
