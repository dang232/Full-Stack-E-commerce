package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;

import java.util.UUID;

public class ViewOrderUseCase {

    private final OrderRepositoryPort orderRepositoryPort;

    public ViewOrderUseCase(OrderRepositoryPort orderRepositoryPort) {
        this.orderRepositoryPort = orderRepositoryPort;
    }

    public Order view(UUID orderId) {
        return orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
    }
}
