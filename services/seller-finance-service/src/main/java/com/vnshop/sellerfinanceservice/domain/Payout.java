package com.vnshop.sellerfinanceservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Payout {
    private final UUID payoutId;
    private final String sellerId;
    private final BigDecimal amount;
    private PayoutStatus status;
    private final Instant createdAt;

    public Payout(UUID payoutId, String sellerId, BigDecimal amount, PayoutStatus status, Instant createdAt) {
        Objects.requireNonNull(payoutId, "payoutId is required");
        requireNonBlank(sellerId, "sellerId");
        Objects.requireNonNull(amount, "amount is required");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        this.payoutId = payoutId;
        this.sellerId = sellerId;
        this.amount = amount;
        this.status = Objects.requireNonNull(status, "status is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static Payout pending(String sellerId, BigDecimal amount, Instant createdAt) {
        return new Payout(UUID.randomUUID(), sellerId, amount, PayoutStatus.PENDING, createdAt);
    }

    public UUID payoutId() {
        return payoutId;
    }

    public String sellerId() {
        return sellerId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public PayoutStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public void complete() {
        status = PayoutStatus.COMPLETED;
    }

    public void fail() {
        status = PayoutStatus.FAILED;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
