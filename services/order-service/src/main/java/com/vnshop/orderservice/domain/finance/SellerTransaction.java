package com.vnshop.orderservice.domain.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SellerTransaction(
        UUID transactionId,
        String sellerId,
        SellerTransactionType type,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal balanceAfter,
        String idempotencyKey,
        Instant createdAt
) {
    public SellerTransaction {
        Objects.requireNonNull(transactionId, "transactionId is required");
        requireNonBlank(sellerId, "sellerId");
        Objects.requireNonNull(type, "type is required");
        requireNonNegative(amount, "amount");
        requireNonNegative(feeAmount, "feeAmount");
        requireNonNegative(balanceAfter, "balanceAfter");
        requireNonBlank(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static SellerTransaction orderPayment(String sellerId, BigDecimal sellerNet, BigDecimal feeAmount, BigDecimal balanceAfter, String idempotencyKey, Instant createdAt) {
        return new SellerTransaction(UUID.randomUUID(), sellerId, SellerTransactionType.ORDER_PAYMENT, sellerNet, feeAmount, balanceAfter, idempotencyKey, createdAt);
    }

    public static SellerTransaction payout(String sellerId, BigDecimal amount, BigDecimal balanceAfter, String idempotencyKey, Instant createdAt) {
        return new SellerTransaction(UUID.randomUUID(), sellerId, SellerTransactionType.PAYOUT, amount, BigDecimal.ZERO, balanceAfter, idempotencyKey, createdAt);
    }

    private static void requireNonNegative(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
