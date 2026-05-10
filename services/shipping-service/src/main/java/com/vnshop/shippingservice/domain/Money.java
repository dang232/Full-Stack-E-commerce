package com.vnshop.shippingservice.domain;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {
    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
    }

    public static Money vnd(long amount) {
        return new Money(BigDecimal.valueOf(amount), "VND");
    }
}
