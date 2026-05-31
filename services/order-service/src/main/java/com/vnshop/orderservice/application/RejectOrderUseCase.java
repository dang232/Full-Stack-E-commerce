package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.InventoryReservationPort;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;

import java.util.Objects;
import java.util.UUID;

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

    public Order reject(UUID orderId, String sellerId) {
        Order order = findOrder(orderId);
        findSellerSubOrder(order, sellerId).reject();
        inventoryReservationPort.release(order.id().toString());
        Order savedOrder = orderRepository.save(order);
        orderEventPublisherPort.publishOrderUpdated(savedOrder);
        return savedOrder;
    }

    private Order findOrder(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId is required");
        // Pt40 audit: same fold as Ship/Accept (gotcha #106).
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderAccessDeniedException("not authorized to reject this order"));
    }

    private SubOrder findSellerSubOrder(Order order, String sellerId) {
        requireNonBlank(sellerId, "sellerId");
        return order.subOrders().stream()
                .filter(subOrder -> subOrder.sellerId().equals(sellerId))
                .findFirst()
                // Pt38 audit (extends pt37): the prior IllegalArgumentException
                // mapped to 400 (wrong status) and embedded the requested
                // sellerId in the message (oracle). Constant-message OAD
                // makes every probe response identical.
                .orElseThrow(() -> new OrderAccessDeniedException("not authorized to reject this order"));
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
