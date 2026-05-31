package com.vnshop.productservice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        try {
            amount = amount.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("VND amount cannot have decimals");
        }
    }
}
