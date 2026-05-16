package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.DashboardSummary;
import com.vnshop.orderservice.domain.RevenueTimeSeries;
import com.vnshop.orderservice.domain.TopItem;
import com.vnshop.orderservice.domain.port.out.DashboardAnalyticsPort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dashboard read-side projections. Previously decorated with {@code @Cacheable},
 * but Spring Cache + Redis + Jackson polymorphic typing could not be made to
 * round-trip the domain records cleanly under Boot 4 / Spring 7 / Jackson 2.21
 * — first call wrote, second call returned a {@code LinkedHashMap} that ClassCast'd.
 * Recomputing on every request is acceptable: each method is one Postgres query
 * and dashboard queries are admin-only, low-traffic. Add caching back if/when
 * traffic warrants it, but use a typed serializer (e.g. one per method).
 */
public class GetDashboardUseCase {
    private static final int DAYS_IN_PERIOD = 30;
    private static final int TOP_ITEM_LIMIT = 10;

    private final DashboardAnalyticsPort analytics;

    public GetDashboardUseCase(DashboardAnalyticsPort analytics) {
        this.analytics = analytics;
    }

    public DashboardSummary summary() {
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(DAYS_IN_PERIOD - 1L);
        long totalOrders = analytics.countByDateBetween(periodStart, periodEnd);
        BigDecimal totalRevenue = analytics.sumRevenueByDateBetween(periodStart, periodEnd);
        long activeBuyers = analytics.countDistinctBuyerId(periodStart, periodEnd);
        long activeSellers = analytics.countDistinctSellerId(periodStart, periodEnd);
        BigDecimal averageOrderValue = totalOrders == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

        return new DashboardSummary(totalOrders, totalRevenue, activeBuyers, activeSellers, averageOrderValue, periodStart, periodEnd);
    }

    public RevenueTimeSeries revenue() {
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(DAYS_IN_PERIOD - 1L);
        Map<LocalDate, BigDecimal> revenueByDate = analytics.revenueByDateBetween(periodStart, periodEnd).stream()
                .collect(Collectors.toMap(DashboardAnalyticsPort.RevenueByDate::date, DashboardAnalyticsPort.RevenueByDate::revenue));
        List<RevenueTimeSeries.Point> points = new ArrayList<>();

        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            points.add(new RevenueTimeSeries.Point(date, revenueByDate.getOrDefault(date, BigDecimal.ZERO)));
        }

        return new RevenueTimeSeries(points);
    }

    public List<TopItem> topProducts() {
        return analytics.topProducts(TOP_ITEM_LIMIT).stream()
                .map(m -> new TopItem(m.id(), m.name(), m.value()))
                .toList();
    }

    public List<TopItem> topSellers() {
        return analytics.topSellers(TOP_ITEM_LIMIT).stream()
                .map(m -> new TopItem(m.id(), m.name(), m.value()))
                .toList();
    }
}
