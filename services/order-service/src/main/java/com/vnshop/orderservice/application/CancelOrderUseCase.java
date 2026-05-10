package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.port.out.InventoryReservationPort;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;

import java.util.Objects;

public class CancelOrderUseCase {
    private final OrderRepositoryPort orderRepository;
    private final InventoryReservationPort inventoryReservationPort;
    private final OrderEventPublisherPort orderEventPublisherPort;

    public CancelOrderUseCase(
            OrderRepositoryPort orderRepository,
            InventoryReservationPort inventoryReservationPort,
            OrderEventPublisherPort orderEventPublisherPort
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository is required");
        this.inventoryReservationPort = Objects.requireNonNull(inventoryReservationPort, "inventoryReservationPort is required");
        this.orderEventPublisherPort = Objects.requireNonNull(orderEventPublisherPort, "orderEventPublisherPort is required");
    }

    public Order cancel(String orderId, String buyerId) {
        requireNonBlank(orderId, "orderId");
        requireNonBlank(buyerId, "buyerId");
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        if (!order.buyerId().equals(buyerId)) {
            throw new IllegalArgumentException("order does not belong to buyer: " + buyerId);
        }
        order.subOrders().forEach(subOrder -> {
            if (subOrder.fulfillmentStatus().name().equals("PENDING_ACCEPTANCE")
                    || subOrder.fulfillmentStatus().name().equals("ACCEPTED")) {
                subOrder.cancel();
            }
        });
        inventoryReservationPort.release(order.id());
        order.markPaymentFailed();
        Order savedOrder = orderRepository.save(order);
        orderEventPublisherPort.publishOrderUpdated(savedOrder);
        return savedOrder;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
