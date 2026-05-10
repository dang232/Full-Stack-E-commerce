package com.vnshop.productservice.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {
    public Money(BigDecimal amount) {
        this(amount, "VND");
    }

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        currency = currency == null || currency.isBlank() ? "VND" : currency;
        if (!"VND".equals(currency)) {
            throw new IllegalArgumentException("currency must be VND");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        if (amount.scale() != 0) {
            throw new IllegalArgumentException("VND amount cannot have decimals");
        }
    }
}
