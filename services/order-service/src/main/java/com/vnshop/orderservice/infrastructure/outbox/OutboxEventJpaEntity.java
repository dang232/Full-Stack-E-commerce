package com.vnshop.orderservice.infrastructure.outbox;

import com.vnshop.orderservice.infrastructure.persistence.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "outbox_events", schema = "order_svc")
@Getter
@Setter
public class OutboxEventJpaEntity extends BaseJpaEntity {
    private static final int LAST_ERROR_MAX_LENGTH = 2_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEvent.Status status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    protected OutboxEventJpaEntity() {
    }

    private OutboxEventJpaEntity(
            Long id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            OutboxEvent.Status status,
            int attemptCount,
            Instant nextAttemptAt,
            String lastError
    ) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt == null ? Instant.now() : nextAttemptAt;
        this.lastError = lastError;
    }

    public static OutboxEventJpaEntity fromDomain(OutboxEvent event) {
        return new OutboxEventJpaEntity(
                event.id(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.payload(),
                event.status(),
                event.attemptCount(),
                event.nextAttemptAt(),
                event.lastError()
        );
    }

    public OutboxEvent toDomain() {
        return new OutboxEvent(
                id,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                status,
                getCreatedAt(),
                attemptCount,
                nextAttemptAt,
                lastError
        );
    }

    public void markPublished() {
        status = OutboxEvent.Status.PUBLISHED;
        lastError = null;
    }

    public void recordFailure(int maxAttempts, Exception cause) {
        attemptCount++;
        lastError = errorMessage(cause);
        if (attemptCount >= maxAttempts) {
            status = OutboxEvent.Status.DEAD;
            return;
        }
        nextAttemptAt = Instant.now().plusSeconds(backoffSeconds(attemptCount));
    }

    private static long backoffSeconds(int attempts) {
        return Math.min(300, 1L << Math.min(attempts, 8));
    }

    private static String errorMessage(Exception cause) {
        String message = cause.getClass().getName() + ": " + cause.getMessage();
        if (message.length() <= LAST_ERROR_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
