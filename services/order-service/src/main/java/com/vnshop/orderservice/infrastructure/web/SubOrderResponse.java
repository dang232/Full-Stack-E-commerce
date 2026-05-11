package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.SubOrder;

import java.util.List;

public record SubOrderResponse(
        Long subOrderId,
        String sellerId,
        String fulfillmentStatus,
        MoneyResponse shippingCost,
        String shippingMethod,
        String carrier,
        String trackingNumber,
        List<OrderItemResponse> items
) {

    static SubOrderResponse fromDomain(SubOrder subOrder) {
        return new SubOrderResponse(
                subOrder.id(),
                subOrder.sellerId(),
                subOrder.fulfillmentStatus().name(),
                MoneyResponse.fromDomain(subOrder.shippingCost()),
                subOrder.shippingMethod(),
                subOrder.carrier(),
                subOrder.trackingNumber(),
                subOrder.items().stream().map(OrderItemResponse::fromDomain).toList()
        );
    }
}
