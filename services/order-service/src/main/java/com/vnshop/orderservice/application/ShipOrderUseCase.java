package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;

import java.util.Objects;
import java.util.UUID;

public class ShipOrderUseCase {
    private final OrderRepositoryPort orderRepository;
    private final OrderEventPublisherPort orderEventPublisherPort;

    public ShipOrderUseCase(OrderRepositoryPort orderRepository, OrderEventPublisherPort orderEventPublisherPort) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository is required");
        this.orderEventPublisherPort = Objects.requireNonNull(orderEventPublisherPort, "orderEventPublisherPort is required");
    }

    public Order ship(ShipOrderCommand command) {
        requireNonBlank(command.carrier(), "carrier");
        requireNonBlank(command.trackingNumber(), "trackingNumber");
        Order order = findOrder(command.orderId());
        SubOrder subOrder = findSellerSubOrder(order, command.sellerId());
        subOrder.pack();
        subOrder.ship(command.carrier(), command.trackingNumber());
        Order savedOrder = orderRepository.save(order);
        orderEventPublisherPort.publishOrderUpdated(savedOrder);
        return savedOrder;
    }

    private Order findOrder(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId is required");
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
    }

    private SubOrder findSellerSubOrder(Order order, String sellerId) {
        requireNonBlank(sellerId, "sellerId");
        return order.subOrders().stream()
                .filter(subOrder -> subOrder.sellerId().equals(sellerId))
                .findFirst()
                // Pt37 audit: was IllegalArgumentException, which the
                // ApiExceptionHandler maps to 400. Two problems with that:
                // 1) HTTP semantics — "you tried to ship someone else's
                //    order" is 403, not 400.
                // 2) The old message included the requested sellerId,
                //    handing a malicious caller an oracle for whether
                //    that seller had a sub-order on this order. Generic
                //    "not authorized" message keeps the response shape
                //    the same regardless of which seller probes.
                .orElseThrow(() -> new OrderAccessDeniedException("not authorized to ship this order"));
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
