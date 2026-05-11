package com.vnshop.paymentservice.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank String orderId,
        @NotBlank String buyerId,
        @NotNull @Positive BigDecimal amount
) {
}
