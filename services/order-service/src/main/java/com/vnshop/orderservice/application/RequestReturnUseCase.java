package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;

import java.util.Objects;
import java.util.UUID;

public class RequestReturnUseCase {
    private final OrderRepositoryPort orderRepository;
    private final ReturnRepositoryPort returnRepository;

    public RequestReturnUseCase(OrderRepositoryPort orderRepository, ReturnRepositoryPort returnRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository is required");
        this.returnRepository = Objects.requireNonNull(returnRepository, "returnRepository is required");
    }

    public Return request(String buyerId, Long subOrderId, String reason) {
        requireNonBlank(buyerId, "buyerId");
        Objects.requireNonNull(subOrderId, "subOrderId is required");
        requireNonBlank(reason, "reason");
        Order order = orderRepository.findBySubOrderId(subOrderId)
                .orElseThrow(() -> new IllegalArgumentException("subOrder not found: " + subOrderId));
        if (!order.buyerId().equals(buyerId)) {
            throw new IllegalArgumentException("return buyer does not own order");
        }
        SubOrder subOrder = order.subOrders().stream()
                .filter(candidate -> subOrderId.equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("subOrder not found: " + subOrderId));
        if (subOrder.carrier() == null || subOrder.trackingNumber() == null) {
            throw new IllegalStateException("return can be requested after shipment");
        }
        return returnRepository.save(new Return(UUID.randomUUID().toString(), order.id(), subOrderId, buyerId, reason));
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
