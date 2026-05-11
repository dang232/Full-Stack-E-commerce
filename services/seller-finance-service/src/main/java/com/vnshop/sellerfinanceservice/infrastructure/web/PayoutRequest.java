package com.vnshop.sellerfinanceservice.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PayoutRequest(@NotNull @Positive BigDecimal amount) {
}
