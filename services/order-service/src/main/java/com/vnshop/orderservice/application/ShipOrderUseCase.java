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
                .orElseThrow(() -> new IllegalArgumentException("subOrder not found for seller: " + sellerId));
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
