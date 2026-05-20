package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.CheckoutOrderUseCase.CheckoutLineItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CheckoutRequest(
        @Valid @NotNull AddressRequest shippingAddress,
        @Valid @NotEmpty List<OrderItemRequest> items) {

    List<CheckoutLineItem> toLineItems() {
        return items.stream().map(OrderItemRequest::toLineItem).toList();
    }
}
