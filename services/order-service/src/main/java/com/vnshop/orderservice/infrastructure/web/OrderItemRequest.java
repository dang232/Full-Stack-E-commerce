package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.CheckoutOrderUseCase.CheckoutLineItem;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Wire shape for a checkout line item: only what the client legitimately
 * knows ({@code productId}, optionally a chosen {@code variantSku}, and
 * {@code quantity}). Price, seller, name, and image come from the
 * product-service server-side at checkout time — never trust the client
 * with anything that affects total amount or seller routing.
 */
public record OrderItemRequest(
        @NotBlank String productId,
        String variantSku,
        @Min(1) int quantity
) {
    CheckoutLineItem toLineItem() {
        return new CheckoutLineItem(productId, variantSku, quantity);
    }
}
