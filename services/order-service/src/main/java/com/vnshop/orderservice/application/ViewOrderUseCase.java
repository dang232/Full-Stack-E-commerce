package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;

import java.util.Objects;
import java.util.UUID;

public class ViewOrderUseCase {

    private final OrderRepositoryPort orderRepositoryPort;

    public ViewOrderUseCase(OrderRepositoryPort orderRepositoryPort) {
        this.orderRepositoryPort = orderRepositoryPort;
    }

    /**
     * Internal lookup — does NOT check buyer ownership. Use this only from
     * trusted server-side callers (other use cases that have already
     * authorised the buyer). HTTP controllers MUST go through
     * {@link #viewForBuyer(UUID, String)} so a buyer cannot read another
     * buyer's order by guessing its UUID.
     */
    public Order view(UUID orderId) {
        return orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
    }

    /**
     * Buyer-scoped read. Confirms the authenticated buyer owns the order
     * before returning it. Closes an IDOR where any authenticated buyer
     * could read any other buyer's order details (shipping address, item
     * list, prices) by guessing its UUID.
     */
    public Order viewForBuyer(UUID orderId, String buyerId) {
        Objects.requireNonNull(buyerId, "buyerId is required");
        Order order = view(orderId);
        if (!order.buyerId().equals(buyerId)) {
            throw new OrderAccessDeniedException(
                    "buyer " + buyerId + " does not own order " + orderId);
        }
        return order;
    }
}
