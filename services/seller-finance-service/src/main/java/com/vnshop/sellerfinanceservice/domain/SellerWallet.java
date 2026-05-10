package com.vnshop.sellerfinanceservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class SellerWallet {
    private final String sellerId;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal totalEarned;
    private Instant lastPayoutAt;

    public SellerWallet(String sellerId) {
        this(sellerId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    public SellerWallet(String sellerId, BigDecimal availableBalance, BigDecimal pendingBalance, BigDecimal totalEarned, Instant lastPayoutAt) {
        requireNonBlank(sellerId, "sellerId");
        this.sellerId = sellerId;
        this.availableBalance = requireNonNegative(availableBalance, "availableBalance");
        this.pendingBalance = requireNonNegative(pendingBalance, "pendingBalance");
        this.totalEarned = requireNonNegative(totalEarned, "totalEarned");
        this.lastPayoutAt = lastPayoutAt;
    }

    public String sellerId() {
        return sellerId;
    }

    public BigDecimal availableBalance() {
        return availableBalance;
    }

    public BigDecimal pendingBalance() {
        return pendingBalance;
    }

    public BigDecimal totalEarned() {
        return totalEarned;
    }

    public Instant lastPayoutAt() {
        return lastPayoutAt;
    }

    public void credit(BigDecimal amount) {
        BigDecimal creditAmount = requirePositive(amount, "amount");
        availableBalance = availableBalance.add(creditAmount);
        totalEarned = totalEarned.add(creditAmount);
    }

    public void reservePayout(BigDecimal amount) {
        BigDecimal payoutAmount = requirePositive(amount, "amount");
        if (availableBalance.compareTo(payoutAmount) < 0) {
            throw new IllegalArgumentException("available balance is insufficient");
        }
        availableBalance = availableBalance.subtract(payoutAmount);
        pendingBalance = pendingBalance.add(payoutAmount);
    }

    public void completePayout(BigDecimal amount, Instant completedAt) {
        BigDecimal payoutAmount = requirePositive(amount, "amount");
        if (pendingBalance.compareTo(payoutAmount) < 0) {
            throw new IllegalArgumentException("pending balance is insufficient");
        }
        pendingBalance = pendingBalance.subtract(payoutAmount);
        lastPayoutAt = Objects.requireNonNull(completedAt, "completedAt is required");
    }

    public void failPayout(BigDecimal amount) {
        BigDecimal payoutAmount = requirePositive(amount, "amount");
        if (pendingBalance.compareTo(payoutAmount) < 0) {
            throw new IllegalArgumentException("pending balance is insufficient");
        }
        pendingBalance = pendingBalance.subtract(payoutAmount);
        availableBalance = availableBalance.add(payoutAmount);
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
        return value;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
