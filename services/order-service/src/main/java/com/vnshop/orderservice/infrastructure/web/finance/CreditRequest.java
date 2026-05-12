package com.vnshop.orderservice.infrastructure.web.finance;

import com.vnshop.orderservice.domain.finance.CommissionTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreditRequest(
        @NotNull @Positive BigDecimal orderAmount,
        @NotNull CommissionTier tier,
        @NotBlank String idempotencyKey
) {
}
