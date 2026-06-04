package com.vnshop.orderservice.application.query;

import com.vnshop.orderservice.domain.port.out.OrderSummaryQueryPort;
import com.vnshop.orderservice.domain.projection.OrderSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderQueryHandler {

    private final OrderSummaryQueryPort orderSummaryQueryPort;

    public OrderQueryHandler(OrderSummaryQueryPort orderSummaryQueryPort) {
        this.orderSummaryQueryPort = orderSummaryQueryPort;
    }

    /**
     * Returns all orders for a buyer ordered by createdAt DESC.
     * <p>Kept for callers that want the full list (e.g. invoices, exports).
     */
    public List<OrderSummaryProjection> findByBuyerId(String buyerId) {
        return orderSummaryQueryPort.findByBuyerId(buyerId);
    }

    /**
     * Paged variant with optional status filter. Used by the buyer-facing
     * GET /orders endpoint, which the FE pages through with ?page&size&status.
     */
    public Page<OrderSummaryProjection> findByBuyerId(String buyerId, String status, Pageable pageable) {
        return orderSummaryQueryPort.findByBuyerId(buyerId, status, pageable);
    }

    public Optional<OrderSummaryProjection> findByOrderId(String orderId) {
        return orderSummaryQueryPort.findByOrderId(orderId);
    }
}
