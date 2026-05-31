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

    /**
     * One seller's gross revenue (line-item total) and distinct sub-order count
     * grouped by UTC date for the closed range [startDate, endDate]. Days with
     * no orders are simply absent from the result; the use case pads them.
     */
    List<SellerRevenueByDate> sellerRevenueByDateBetween(String sellerId, LocalDate startDate, LocalDate endDate);

    record RevenueByDate(LocalDate date, BigDecimal revenue) {}

    record TopMetric(String id, String name, BigDecimal value) {}

    record SellerRevenueByDate(LocalDate date, BigDecimal revenue, long orderCount) {}
}
