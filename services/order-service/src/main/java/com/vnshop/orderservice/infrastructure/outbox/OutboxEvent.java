package com.vnshop.orderservice.infrastructure.outbox;

import java.time.Instant;

public record OutboxEvent(
        Long id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        OutboxEvent.Status status,
        Instant createdAt,
        int attemptCount,
        Instant nextAttemptAt,
        String lastError
) {
    public enum Status {
        PENDING,
        PUBLISHED,
        DEAD
    }

    public static OutboxEvent pending(String aggregateType, String aggregateId, String eventType, String payload) {
        return new OutboxEvent(null, aggregateType, aggregateId, eventType, payload, Status.PENDING, Instant.now(), 0, Instant.now(), null);
    }
}
