package com.vnshop.orderservice.infrastructure.web.finance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PayoutRequest(@NotNull @Positive BigDecimal amount) {
}
