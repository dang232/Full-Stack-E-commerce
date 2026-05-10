package com.vnshop.paymentservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Payment {
    private final String paymentId;
    private final String orderId;
    private final String buyerId;
    private final BigDecimal amount;
    private final Method method;
    private final PaymentStatus status;
    private final String transactionRef;
    private final Instant createdAt;

    public Payment(String paymentId, String orderId, String buyerId, BigDecimal amount, Method method, PaymentStatus status, String transactionRef, Instant createdAt) {
        this.paymentId = requireNonBlank(paymentId, "paymentId");
        this.orderId = requireNonBlank(orderId, "orderId");
        this.buyerId = requireNonBlank(buyerId, "buyerId");
        this.amount = Objects.requireNonNull(amount, "amount is required");
        this.method = Objects.requireNonNull(method, "method is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.transactionRef = transactionRef;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static Payment pending(String orderId, String buyerId, BigDecimal amount, Method method) {
        return new Payment(UUID.randomUUID().toString(), orderId, buyerId, amount, method, PaymentStatus.PENDING, null, Instant.now());
    }

    public Payment withResult(PaymentStatus status, String transactionRef) {
        return new Payment(paymentId, orderId, buyerId, amount, method, status, transactionRef, createdAt);
    }

    public String paymentId() {
        return paymentId;
    }

    public String orderId() {
        return orderId;
    }

    public String buyerId() {
        return buyerId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public Method method() {
        return method;
    }

    public PaymentStatus status() {
        return status;
    }

    public String transactionRef() {
        return transactionRef;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    public enum Method {
        COD,
        VNPAY,
        MOMO
    }
}
