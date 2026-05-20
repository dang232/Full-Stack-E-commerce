package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import com.vnshop.orderservice.domain.port.out.RefundRequestPort;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;

import java.util.Objects;
import java.util.UUID;

public class CompleteReturnUseCase {
    private final ReturnRepositoryPort returnRepository;
    private final OrderRepositoryPort orderRepository;
    private final RefundRequestPort refundRequestPort;

    public CompleteReturnUseCase(ReturnRepositoryPort returnRepository, OrderRepositoryPort orderRepository, RefundRequestPort refundRequestPort) {
        this.returnRepository = Objects.requireNonNull(returnRepository, "returnRepository is required");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository is required");
        this.refundRequestPort = Objects.requireNonNull(refundRequestPort, "refundRequestPort is required");
    }

    /**
     * Pt14 audit fix: only the seller who owns the SubOrder being returned
     * may complete. The seller is the natural completer because they ship
     * the refund — admins go through a separate admin endpoint.
     */
    public Return complete(UUID returnId, String sellerId) {
        Return orderReturn = returnRepository.findById(returnId)
                .orElseThrow(() -> new IllegalArgumentException("return not found: " + returnId));
        ReturnAuthorization.requireSellerOwnsReturn(orderRepository, orderReturn, sellerId);
        Order order = orderRepository.findById(UUID.fromString(orderReturn.orderId()))
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderReturn.orderId()));
        Money refundAmount = order.subOrders().stream()
                .filter(subOrder -> orderReturn.subOrderId().equals(subOrder.id()))
                .findFirst()
                .map(SubOrder::itemsTotal)
                .orElseThrow(() -> new IllegalArgumentException("subOrder not found: " + orderReturn.subOrderId()));
        orderReturn.complete();
        Return savedReturn = returnRepository.save(orderReturn);
        refundRequestPort.requestRefund(savedReturn, refundAmount);
        return savedReturn;
    }
}
