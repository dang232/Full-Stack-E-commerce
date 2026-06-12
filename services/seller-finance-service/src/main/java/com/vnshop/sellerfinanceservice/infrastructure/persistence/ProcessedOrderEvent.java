package com.vnshop.sellerfinanceservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_order_event")
public class ProcessedOrderEvent {

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedOrderEvent() {
    }

    public ProcessedOrderEvent(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public String eventId() {
        return eventId;
    }

    public Instant processedAt() {
        return processedAt;
    }
}
