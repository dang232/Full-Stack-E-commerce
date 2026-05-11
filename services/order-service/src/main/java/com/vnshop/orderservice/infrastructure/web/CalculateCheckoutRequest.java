package com.vnshop.orderservice.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CalculateCheckoutRequest(
        @NotBlank String cartId,
        @Valid @NotNull AddressRequest shippingAddress,
        String couponCode
) {
}
