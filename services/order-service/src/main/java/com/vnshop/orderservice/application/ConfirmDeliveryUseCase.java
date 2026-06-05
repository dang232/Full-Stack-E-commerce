package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;

import java.util.Objects;
import java.util.UUID;

public class ConfirmDeliveryUseCase {
    private final OrderRepositoryPort orderRepository;
    private final OrderEventPublisherPort orderEventPublisherPort;

    public ConfirmDeliveryUseCase(OrderRepositoryPort orderRepository,
                                  OrderEventPublisherPort orderEventPublisherPort) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository is required");
        this.orderEventPublisherPort = Objects.requireNonNull(orderEventPublisherPort, "orderEventPublisherPort is required");
    }

    public void confirm(UUID orderId, Long subOrderId, String buyerId) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(subOrderId, "subOrderId is required");
        requireNonBlank(buyerId, "buyerId");

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderAccessDeniedException("not authorized to confirm delivery for this order"));

        if (!order.buyerId().equals(buyerId)) {
            throw new OrderAccessDeniedException("not authorized to confirm delivery for this order");
        }

        SubOrder subOrder = order.subOrders().stream()
                .filter(so -> subOrderId.equals(so.id()))
                .findFirst()
                .orElseThrow(() -> new OrderAccessDeniedException("not authorized to confirm delivery for this order"));

        subOrder.confirmDelivery();
        Order savedOrder = orderRepository.save(order);
        orderEventPublisherPort.publishOrderDelivered(savedOrder, subOrder);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
