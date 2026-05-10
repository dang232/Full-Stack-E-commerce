package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {
    Order save(Order order);

    Optional<Order> findById(String orderId);

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByBuyerId(String buyerId);

    Optional<Order> findBySubOrderId(Long subOrderId);
}
