package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;

import java.util.UUID;

/**
 * Shared seller-ownership check for the {@code /returns/{id}/approve|reject|complete}
 * endpoints. Each return is associated with exactly one SubOrder, and the
 * SubOrder carries the authoritative sellerId. Callers (always the seller
 * acting on a return for one of their own SubOrders) must therefore match
 * that sellerId or the request is refused.
 *
 * <p>Closes the post-pt13 audit-pass IDORs where any authenticated seller
 * could approve / reject / complete any other seller's return by guessing
 * the returnId UUID.
 */
final class ReturnAuthorization {
    private ReturnAuthorization() {
    }

    static void requireSellerOwnsReturn(OrderRepositoryPort orderRepository, Return orderReturn, String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("sellerId is required");
        }
        Order order = orderRepository.findById(UUID.fromString(orderReturn.orderId()))
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderReturn.orderId()));
        SubOrder subOrder = order.subOrders().stream()
                .filter(candidate -> orderReturn.subOrderId().equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("subOrder not found: " + orderReturn.subOrderId()));
        if (!sellerId.equals(subOrder.sellerId())) {
            throw new OrderAccessDeniedException(
                    "seller " + sellerId + " does not own return " + orderReturn.returnId());
        }
    }
}
