package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.FulfillmentStatus;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;

import java.util.List;
import java.util.Objects;

public class SellerOrderQueryUseCase {
    private final OrderRepositoryPort orderRepository;

    public SellerOrderQueryUseCase(OrderRepositoryPort orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository is required");
    }

    public List<Order> findPending(String sellerId) {
        requireNonBlank(sellerId, "sellerId");
        return orderRepository.findBySellerIdAndFulfillmentStatus(sellerId, FulfillmentStatus.PENDING_ACCEPTANCE);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
