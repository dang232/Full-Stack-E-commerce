package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.OrderItem;

public record OrderItemResponse(
        String productId,
        String variantSku,
        String sellerId,
        String name,
        int quantity,
        MoneyResponse unitPrice,
        String imageUrl) {

    static OrderItemResponse fromDomain(OrderItem item) {
        return new OrderItemResponse(
                item.productId(),
                item.variantSku(),
                item.sellerId(),
                item.name(),
                item.quantity(),
                MoneyResponse.fromDomain(item.unitPrice()),
                item.imageUrl()
        );
    }
}
