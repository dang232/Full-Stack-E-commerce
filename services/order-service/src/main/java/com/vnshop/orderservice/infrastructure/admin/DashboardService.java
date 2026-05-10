package com.vnshop.orderservice.infrastructure.admin;

import com.vnshop.orderservice.infrastructure.persistence.OrderJpaRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    private static final int DAYS_IN_PERIOD = 30;
    private static final int TOP_ITEM_LIMIT = 10;

    private final OrderJpaRepository orderRepository;

    public DashboardService(OrderJpaRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Cacheable(cacheNames = "dashboardSummary", key = "'last30Days'")
    public DashboardSummary summary() {
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(DAYS_IN_PERIOD - 1L);
        long totalOrders = orderRepository.countByDateBetween(periodStart, periodEnd);
        BigDecimal totalRevenue = orderRepository.sumRevenueByDateBetween(periodStart, periodEnd);
        long activeBuyers = orderRepository.countDistinctBuyerId(periodStart, periodEnd);
        long activeSellers = orderRepository.countDistinctSellerId(periodStart, periodEnd);
        BigDecimal averageOrderValue = totalOrders == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

        return new DashboardSummary(totalOrders, totalRevenue, activeBuyers, activeSellers, averageOrderValue, periodStart, periodEnd);
    }

    @Cacheable(cacheNames = "dashboardRevenue", key = "'last30Days:day'")
    public RevenueTimeSeries revenue() {
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(DAYS_IN_PERIOD - 1L);
        Map<LocalDate, BigDecimal> revenueByDate = orderRepository.revenueByDateBetween(periodStart, periodEnd).stream()
                .collect(Collectors.toMap(OrderJpaRepository.RevenueByDate::date, OrderJpaRepository.RevenueByDate::revenue));
        List<RevenueTimeSeries.Point> points = new ArrayList<>();

        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            points.add(new RevenueTimeSeries.Point(date, revenueByDate.getOrDefault(date, BigDecimal.ZERO)));
        }

        return new RevenueTimeSeries(points);
    }

    @Cacheable(cacheNames = "dashboardTopProducts", key = "'last30Days:limit10'")
    public List<TopItem> topProducts() {
        return orderRepository.topProducts(TOP_ITEM_LIMIT).stream()
                .map(item -> new TopItem(item.id(), item.name(), item.value()))
                .toList();
    }

    @Cacheable(cacheNames = "dashboardTopSellers", key = "'last30Days:limit10'")
    public List<TopItem> topSellers() {
        return orderRepository.topSellers(TOP_ITEM_LIMIT).stream()
                .map(item -> new TopItem(item.id(), item.name(), item.value()))
                .toList();
    }
}
