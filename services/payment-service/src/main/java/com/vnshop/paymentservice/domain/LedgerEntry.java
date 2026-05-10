package com.vnshop.paymentservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class LedgerEntry {
    private final String transactionId;
    private final String orderId;
    private final String debitAccount;
    private final String creditAccount;
    private final BigDecimal amount;
    private final String currency;
    private final Instant timestamp;
    private final String status;
    private final String description;

    public LedgerEntry(String transactionId, String orderId, String debitAccount, String creditAccount, BigDecimal amount, String currency, Instant timestamp, String status, String description) {
        this.transactionId = requireNonBlank(transactionId, "transactionId");
        this.orderId = requireNonBlank(orderId, "orderId");
        this.debitAccount = requireNonBlank(debitAccount, "debitAccount");
        this.creditAccount = requireNonBlank(creditAccount, "creditAccount");
        this.amount = Objects.requireNonNull(amount, "amount is required");
        this.currency = requireNonBlank(currency, "currency");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp is required");
        this.status = requireNonBlank(status, "status");
        this.description = description;
    }

    public String transactionId() {
        return transactionId;
    }

    public String orderId() {
        return orderId;
    }

    public String debitAccount() {
        return debitAccount;
    }

    public String creditAccount() {
        return creditAccount;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String status() {
        return status;
    }

    public String description() {
        return description;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
