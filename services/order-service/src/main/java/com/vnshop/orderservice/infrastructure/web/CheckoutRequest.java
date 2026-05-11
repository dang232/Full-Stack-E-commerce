package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.OrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CheckoutRequest(
        @Valid @NotNull AddressRequest shippingAddress,
        @Valid @NotEmpty List<OrderItemRequest> items) {

    List<OrderItem> toItems() {
        return items.stream().map(OrderItemRequest::toDomain).toList();
    }
}
