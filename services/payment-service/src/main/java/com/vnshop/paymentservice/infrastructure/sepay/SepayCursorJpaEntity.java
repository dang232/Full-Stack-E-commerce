package com.vnshop.paymentservice.infrastructure.sepay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(schema = "payment_svc", name = "sepay_cursor")
public class SepayCursorJpaEntity {
    @Id
    @Column(name = "id")
    private Integer id = 1;

    @Column(name = "last_tx_id")
    private String lastTxId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Integer getId() {
        return id;
    }

    public String getLastTxId() {
        return lastTxId;
    }

    public void setLastTxId(String lastTxId) {
        this.lastTxId = lastTxId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
