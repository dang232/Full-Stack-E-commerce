package com.vnshop.orderservice.infrastructure.outbox;

import java.time.Instant;

public record OutboxEvent(
        Long id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        OutboxEvent.Status status,
        Instant createdAt
) {
    public enum Status {
        PENDING,
        PUBLISHED
    }

    public static OutboxEvent pending(String aggregateType, String aggregateId, String eventType, String payload) {
        return new OutboxEvent(null, aggregateType, aggregateId, eventType, payload, Status.PENDING, Instant.now());
    }
}
