package com.vnshop.paymentservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_dead_letter", schema = "payment_svc")
public class WebhookDeadLetterJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "webhook_id", nullable = false)
    private String webhookId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "retried_at")
    private Instant retriedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    protected WebhookDeadLetterJpaEntity() {}

    public WebhookDeadLetterJpaEntity(UUID id, String webhookId, String provider, String eventType,
                                       String payload, String failureReason, int attempts,
                                       Instant createdAt, Instant retriedAt, int retryCount) {
        this.id = id;
        this.webhookId = webhookId;
        this.provider = provider;
        this.eventType = eventType;
        this.payload = payload;
        this.failureReason = failureReason;
        this.attempts = attempts;
        this.createdAt = createdAt;
        this.retriedAt = retriedAt;
        this.retryCount = retryCount;
    }

    public UUID getId() { return id; }
    public String getWebhookId() { return webhookId; }
    public String getProvider() { return provider; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getFailureReason() { return failureReason; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRetriedAt() { return retriedAt; }
    public int getRetryCount() { return retryCount; }

    public void setRetriedAt(Instant retriedAt) { this.retriedAt = retriedAt; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
}
