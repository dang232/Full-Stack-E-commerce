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
        // Pt40 audit: see ApproveReturnUseCase. Same fold for unknown
        // returnId. The order/subOrder follow-up lookups stay IAE
        // because if the gate has passed (seller owns the return) and
        // we still can't find the underlying order/subOrder, that's a
        // referential-integrity bug — the caller has no probe value
        // because they already proved ownership of a row that points
        // at the missing referent.
        Return orderReturn = returnRepository.findById(returnId)
                .orElseThrow(() -> new OrderAccessDeniedException("not authorized to act on this return"));
        ReturnAuthorization.requireSellerOwnsReturn(orderRepository, orderReturn, sellerId);
        Order order = orderRepository.findById(UUID.fromString(orderReturn.orderId()))
                .orElseThrow(() -> new IllegalStateException("return points at missing order"));
        SubOrder targetSubOrder = order.subOrders().stream()
                .filter(subOrder -> orderReturn.subOrderId().equals(subOrder.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("return points at missing subOrder"));
        Money refundAmount = targetSubOrder.itemsTotal();
        orderReturn.complete();
        Return savedReturn = returnRepository.save(orderReturn);
        refundRequestPort.requestRefund(savedReturn, targetSubOrder.sellerId(), refundAmount);
        return savedReturn;
    }
}
