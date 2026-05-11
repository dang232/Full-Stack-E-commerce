package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class OrderQueryUseCase {
    private final OrderRepositoryPort orderRepository;

    public OrderQueryUseCase(OrderRepositoryPort orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository is required");
    }

    public List<Order> findByBuyerId(String buyerId) {
        requireNonBlank(buyerId, "buyerId");
        return orderRepository.findByBuyerId(buyerId);
    }

    public Order findById(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId is required");
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
    }

    public String findOrderIdBySubOrderId(Long subOrderId) {
        if (subOrderId == null) {
            throw new IllegalArgumentException("subOrderId is required");
        }
        return orderRepository.findOrderIdBySubOrderId(subOrderId)
                .orElseThrow(() -> new IllegalArgumentException("subOrder not found: " + subOrderId));
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
