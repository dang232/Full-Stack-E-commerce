package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.FulfillmentStatus;
import com.vnshop.orderservice.domain.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {
    Order save(Order order);

    Optional<Order> findById(UUID orderId);

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByBuyerId(String buyerId);

    Optional<Order> findBySubOrderId(Long subOrderId);

    Optional<String> findOrderIdBySubOrderId(Long subOrderId);

    List<Order> findBySellerIdAndFulfillmentStatus(String sellerId, FulfillmentStatus status);
}
