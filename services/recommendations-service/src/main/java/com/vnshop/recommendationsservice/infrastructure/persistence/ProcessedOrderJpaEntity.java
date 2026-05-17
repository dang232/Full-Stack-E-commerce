package com.vnshop.recommendationsservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Idempotency guard for the co-purchase aggregator. If a Kafka consumer
 * replay or partition rebalance redelivers the same {@code order.created}
 * event we must not double-count its co-occurrences.
 */
@Entity
@Table(name = "processed_orders")
@Getter
@Setter
public class ProcessedOrderJpaEntity {

    @Id
    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedOrderJpaEntity() {
    }

    public ProcessedOrderJpaEntity(String orderId) {
        this.orderId = orderId;
        this.processedAt = Instant.now();
    }
}
