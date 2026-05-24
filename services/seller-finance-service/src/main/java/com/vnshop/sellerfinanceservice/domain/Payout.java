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
    private String completedBy;
    private Instant completedAt;

    public Payout(UUID payoutId, String sellerId, BigDecimal amount, PayoutStatus status, Instant createdAt) {
        this(payoutId, sellerId, amount, status, createdAt, null, null);
    }

    public Payout(UUID payoutId, String sellerId, BigDecimal amount, PayoutStatus status, Instant createdAt,
                  String completedBy, Instant completedAt) {
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
        this.completedBy = completedBy;
        this.completedAt = completedAt;
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

    public String completedBy() {
        return completedBy;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public void complete(String completedBy, Instant completedAt) {
        requireNonBlank(completedBy, "completedBy");
        Objects.requireNonNull(completedAt, "completedAt is required");
        this.status = PayoutStatus.COMPLETED;
        this.completedBy = completedBy;
        this.completedAt = completedAt;
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
