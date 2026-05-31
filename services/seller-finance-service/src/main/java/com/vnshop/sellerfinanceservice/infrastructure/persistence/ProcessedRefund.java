package com.vnshop.sellerfinanceservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "processed_refund")
public class ProcessedRefund {

    @Id
    @Column(name = "refund_id", length = 64)
    private String refundId;

    @Column(name = "seller_id", nullable = false, length = 64)
    private String sellerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedRefund() {
    }

    public ProcessedRefund(String refundId, String sellerId, BigDecimal amount) {
        this.refundId = refundId;
        this.sellerId = sellerId;
        this.amount = amount;
        this.processedAt = Instant.now();
    }

    public String refundId() {
        return refundId;
    }

    public String sellerId() {
        return sellerId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public Instant processedAt() {
        return processedAt;
    }
}
