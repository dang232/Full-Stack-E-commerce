package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.port.out.DashboardAnalyticsPort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class DashboardAnalyticsAdapter implements DashboardAnalyticsPort {

    private final OrderJpaRepository orderJpaRepository;

    public DashboardAnalyticsAdapter(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public long countByDateBetween(LocalDate startDate, LocalDate endDate) {
        return orderJpaRepository.countByDateBetween(startDate, endDate);
    }

    @Override
    public BigDecimal sumRevenueByDateBetween(LocalDate startDate, LocalDate endDate) {
        return orderJpaRepository.sumRevenueByDateBetween(startDate, endDate);
    }

    @Override
    public long countDistinctBuyerId(LocalDate startDate, LocalDate endDate) {
        return orderJpaRepository.countDistinctBuyerId(startDate, endDate);
    }

    @Override
    public long countDistinctSellerId(LocalDate startDate, LocalDate endDate) {
        return orderJpaRepository.countDistinctSellerId(startDate, endDate);
    }

    @Override
    public List<RevenueByDate> revenueByDateBetween(LocalDate startDate, LocalDate endDate) {
        return orderJpaRepository.revenueByDateBetween(startDate, endDate).stream()
                .map(r -> new RevenueByDate(r.date(), r.revenue()))
                .toList();
    }

    @Override
    public List<TopMetric> topProducts(int limit) {
        return orderJpaRepository.topProducts(limit).stream()
                .map(m -> new TopMetric(m.id(), m.name(), m.value()))
                .toList();
    }

    @Override
    public List<TopMetric> topSellers(int limit) {
        return orderJpaRepository.topSellers(limit).stream()
                .map(m -> new TopMetric(m.id(), m.name(), m.value()))
                .toList();
    }

    @Override
    public List<SellerRevenueByDate> sellerRevenueByDateBetween(String sellerId, LocalDate startDate, LocalDate endDate) {
        return orderJpaRepository.sellerRevenueByDateBetween(sellerId, startDate, endDate).stream()
                .map(r -> new SellerRevenueByDate(r.date(), r.revenue(), r.orderCount()))
                .toList();
    }
}
