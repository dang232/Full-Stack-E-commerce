package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.SellerRevenuePoint;
import com.vnshop.orderservice.domain.port.out.DashboardAnalyticsPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Aggregate one seller's gross revenue and order count per UTC day for the
 * last {@code days} days, padding missing days with zeroes so the FE chart
 * has a continuous x-axis. Bounded to a sane window (1-365 days) to keep
 * the query cheap.
 */
public class GetSellerRevenueUseCase {
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;

    private final DashboardAnalyticsPort analytics;
    private final Clock clock;

    public GetSellerRevenueUseCase(DashboardAnalyticsPort analytics) {
        this(analytics, Clock.systemUTC());
    }

    GetSellerRevenueUseCase(DashboardAnalyticsPort analytics, Clock clock) {
        this.analytics = Objects.requireNonNull(analytics, "analytics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<SellerRevenuePoint> revenueForSeller(String sellerId, int days) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("sellerId must not be blank");
        }
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new IllegalArgumentException("days must be between " + MIN_DAYS + " and " + MAX_DAYS);
        }

        LocalDate end = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate start = end.minusDays(days - 1L);

        Map<LocalDate, DashboardAnalyticsPort.SellerRevenueByDate> byDate = new TreeMap<>();
        for (DashboardAnalyticsPort.SellerRevenueByDate row : analytics.sellerRevenueByDateBetween(sellerId, start, end)) {
            byDate.put(row.date(), row);
        }

        List<SellerRevenuePoint> points = new java.util.ArrayList<>(days);
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            DashboardAnalyticsPort.SellerRevenueByDate row = byDate.get(date);
            if (row == null) {
                points.add(new SellerRevenuePoint(date, java.math.BigDecimal.ZERO, 0L));
            } else {
                points.add(new SellerRevenuePoint(date, row.revenue(), row.orderCount()));
            }
        }
        return points;
    }
}
