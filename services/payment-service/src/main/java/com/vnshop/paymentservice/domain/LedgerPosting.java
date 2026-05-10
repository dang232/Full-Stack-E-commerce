package com.vnshop.paymentservice.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record LedgerPosting(String accountId, LedgerPostingType type, BigDecimal amount, String currency) {
    public LedgerPosting {
        accountId = requireNonBlank(accountId, "accountId");
        type = Objects.requireNonNull(type, "type is required");
        amount = Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        currency = requireNonBlank(currency, "currency");
    }

    public LedgerPosting reverse() {
        LedgerPostingType reverseType = type == LedgerPostingType.DEBIT ? LedgerPostingType.CREDIT : LedgerPostingType.DEBIT;
        return new LedgerPosting(accountId, reverseType, amount, currency);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
