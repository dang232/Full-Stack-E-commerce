package com.vnshop.paymentservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ReconciliationIssue {
    private final Long issueId;
    private final UUID paymentId;
    private final BigDecimal expectedAmount;
    private final BigDecimal actualAmount;
    private final String description;
    private final Instant detectedAt;
    private final boolean resolved;

    public ReconciliationIssue(Long issueId, UUID paymentId, BigDecimal expectedAmount, BigDecimal actualAmount, String description, Instant detectedAt, boolean resolved) {
        this.issueId = issueId;
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId is required");
        this.expectedAmount = Objects.requireNonNull(expectedAmount, "expectedAmount is required");
        this.actualAmount = Objects.requireNonNull(actualAmount, "actualAmount is required");
        this.description = requireNonBlank(description, "description");
        this.detectedAt = Objects.requireNonNull(detectedAt, "detectedAt is required");
        this.resolved = resolved;
    }

    public Long issueId() {
        return issueId;
    }

    public UUID paymentId() {
        return paymentId;
    }

    public BigDecimal expectedAmount() {
        return expectedAmount;
    }

    public BigDecimal actualAmount() {
        return actualAmount;
    }

    public String description() {
        return description;
    }

    public Instant detectedAt() {
        return detectedAt;
    }

    public boolean resolved() {
        return resolved;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
