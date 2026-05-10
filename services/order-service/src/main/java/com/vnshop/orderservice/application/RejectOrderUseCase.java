package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.InventoryReservationPort;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;

import java.util.Objects;

public class RejectOrderUseCase {
    private final OrderRepositoryPort orderRepository;
    private final InventoryReservationPort inventoryReservationPort;
    private final OrderEventPublisherPort orderEventPublisherPort;

    public RejectOrderUseCase(
            OrderRepositoryPort orderRepository,
            InventoryReservationPort inventoryReservationPort,
            OrderEventPublisherPort orderEventPublisherPort
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository is required");
        this.inventoryReservationPort = Objects.requireNonNull(inventoryReservationPort, "inventoryReservationPort is required");
        this.orderEventPublisherPort = Objects.requireNonNull(orderEventPublisherPort, "orderEventPublisherPort is required");
    }

    public Order reject(String orderId, String sellerId) {
        Order order = findOrder(orderId);
        findSellerSubOrder(order, sellerId).reject();
        inventoryReservationPort.release(order.id());
        Order savedOrder = orderRepository.save(order);
        orderEventPublisherPort.publishOrderUpdated(savedOrder);
        return savedOrder;
    }

    private Order findOrder(String orderId) {
        requireNonBlank(orderId, "orderId");
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
