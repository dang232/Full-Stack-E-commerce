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
                .orElseThrow(() -> new OrderAccessDeniedException("not authorized to act on this return"));
        SubOrder subOrder = order.subOrders().stream()
                .filter(candidate -> orderReturn.subOrderId().equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new OrderAccessDeniedException("not authorized to act on this return"));
        if (!sellerId.equals(subOrder.sellerId())) {
            // Pt38 audit (extends pt37): prior message embedded both the
            // sellerId AND the returnId, turning every 403 into a probe
            // oracle for "does seller X own return Y." The exception type
            // was already correct (OAD → 403); only the message needed
            // tightening. Generic constant string keeps the response body
            // identical regardless of which seller probes which return.
            throw new OrderAccessDeniedException("not authorized to act on this return");
        }
    }
}
