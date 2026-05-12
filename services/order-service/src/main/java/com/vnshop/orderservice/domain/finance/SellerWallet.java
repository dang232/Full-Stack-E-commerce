package com.vnshop.orderservice.domain.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class SellerWallet {
    private final String sellerId;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal totalEarned;
    private BigDecimal totalFees;
    private BigDecimal totalWithdrawn;
    private Instant lastPayoutAt;

    public SellerWallet(String sellerId) {
        this(sellerId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    public SellerWallet(
            String sellerId,
            BigDecimal availableBalance,
            BigDecimal pendingBalance,
            BigDecimal totalEarned,
            BigDecimal totalFees,
            BigDecimal totalWithdrawn,
            Instant lastPayoutAt
    ) {
        requireNonBlank(sellerId, "sellerId");
        this.sellerId = sellerId;
        this.availableBalance = requireNonNegative(availableBalance, "availableBalance");
        this.pendingBalance = requireNonNegative(pendingBalance, "pendingBalance");
        this.totalEarned = requireNonNegative(totalEarned, "totalEarned");
        this.totalFees = requireNonNegative(totalFees, "totalFees");
        this.totalWithdrawn = requireNonNegative(totalWithdrawn, "totalWithdrawn");
        this.lastPayoutAt = lastPayoutAt;
    }

    public String sellerId() { return sellerId; }
    public BigDecimal availableBalance() { return availableBalance; }
    public BigDecimal pendingBalance() { return pendingBalance; }
    public BigDecimal totalEarned() { return totalEarned; }
    public BigDecimal totalFees() { return totalFees; }
    public BigDecimal totalWithdrawn() { return totalWithdrawn; }
    public Instant lastPayoutAt() { return lastPayoutAt; }

    public void creditOrderPayment(BigDecimal sellerNet, BigDecimal feeAmount) {
        BigDecimal net = requirePositive(sellerNet, "sellerNet");
        BigDecimal fee = requireNonNegative(feeAmount, "feeAmount");
        availableBalance = availableBalance.add(net);
        totalEarned = totalEarned.add(net);
        totalFees = totalFees.add(fee);
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
        totalWithdrawn = totalWithdrawn.add(payoutAmount);
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
