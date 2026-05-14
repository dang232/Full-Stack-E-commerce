package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.projection.OrderSummaryProjection;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderListItemResponse(
        String orderId,
        String buyerId,
        String sellerId,
        String status,
        BigDecimal totalAmount,
        int itemCount,
        Instant createdAt,
        Instant updatedAt
) {
    static OrderListItemResponse fromProjection(OrderSummaryProjection projection) {
        return new OrderListItemResponse(
                projection.orderId(),
                projection.buyerId(),
                projection.sellerId(),
                projection.status(),
                projection.totalAmount(),
                projection.itemCount(),
                projection.createdAt(),
                projection.updatedAt()
        );
    }
}
