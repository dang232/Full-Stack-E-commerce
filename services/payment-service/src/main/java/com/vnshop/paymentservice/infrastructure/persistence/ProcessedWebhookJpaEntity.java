package com.vnshop.paymentservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(schema = "payment_svc", name = "processed_webhooks")
@IdClass(ProcessedWebhookJpaEntity.ProcessedWebhookId.class)
public class ProcessedWebhookJpaEntity {

    @Id
    @Column(name = "webhook_id", nullable = false, length = 255)
    private String webhookId;

    @Id
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedWebhookJpaEntity() {
    }

    public ProcessedWebhookJpaEntity(String webhookId, String provider, String eventType, Instant processedAt) {
        this.webhookId = Objects.requireNonNull(webhookId, "webhookId is required");
        this.provider = Objects.requireNonNull(provider, "provider is required");
        this.eventType = Objects.requireNonNull(eventType, "eventType is required");
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt is required");
    }

    public String getWebhookId() { return webhookId; }
    public String getProvider() { return provider; }
    public String getEventType() { return eventType; }
    public Instant getProcessedAt() { return processedAt; }

    public static class ProcessedWebhookId implements Serializable {
        private String webhookId;
        private String provider;

        public ProcessedWebhookId() {
        }

        public ProcessedWebhookId(String webhookId, String provider) {
            this.webhookId = webhookId;
            this.provider = provider;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ProcessedWebhookId that)) return false;
            return Objects.equals(webhookId, that.webhookId) && Objects.equals(provider, that.provider);
        }

        @Override
        public int hashCode() {
            return Objects.hash(webhookId, provider);
        }
    }
}
