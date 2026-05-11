package com.vnshop.sellerfinanceservice.infrastructure.web;

import com.vnshop.sellerfinanceservice.domain.CommissionTier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreditRequest(@NotNull @Positive BigDecimal orderAmount, @NotNull CommissionTier tier) {
}
