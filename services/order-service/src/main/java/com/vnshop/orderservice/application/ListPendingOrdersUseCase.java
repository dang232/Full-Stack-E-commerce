package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.FulfillmentStatus;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;

import java.util.List;

public class ListPendingOrdersUseCase {

    private final OrderRepositoryPort orderRepositoryPort;

    public ListPendingOrdersUseCase(OrderRepositoryPort orderRepositoryPort) {
        this.orderRepositoryPort = orderRepositoryPort;
    }

    public List<Order> listPendingBySeller(String sellerId) {
        return orderRepositoryPort.findBySellerIdAndFulfillmentStatus(sellerId, FulfillmentStatus.PENDING_ACCEPTANCE);
    }

    public String orderIdFromSubOrderId(Long subOrderId) {
        return orderRepositoryPort.findOrderIdBySubOrderId(subOrderId)
                .orElseThrow(() -> new IllegalArgumentException("subOrder not found: " + subOrderId));
    }
}
