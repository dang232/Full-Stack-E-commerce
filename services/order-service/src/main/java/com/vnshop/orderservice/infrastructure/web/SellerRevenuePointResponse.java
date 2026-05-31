package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.SellerRevenuePoint;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SellerRevenuePointResponse(LocalDate date, BigDecimal revenue, long orderCount) {
    public static SellerRevenuePointResponse fromDomain(SellerRevenuePoint point) {
        return new SellerRevenuePointResponse(point.date(), point.revenue(), point.orderCount());
    }
}
