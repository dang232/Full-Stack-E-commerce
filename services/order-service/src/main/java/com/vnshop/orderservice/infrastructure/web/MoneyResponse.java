package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.Money;

import java.math.BigDecimal;

public record MoneyResponse(BigDecimal amount, String currency) {

    static MoneyResponse fromDomain(Money money) {
        return new MoneyResponse(money.amount(), money.currency());
    }
}
