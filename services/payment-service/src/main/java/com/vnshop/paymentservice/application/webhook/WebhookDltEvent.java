package com.vnshop.paymentservice.application.webhook;

import java.time.Instant;

/**
 * Kafka message published to {@code payment.webhooks.dlt} when a webhook
 * fails processing after the maximum number of retries.
 */
public record WebhookDltEvent(
        String webhookId,
        String provider,
        String eventType,
        String payload,
        String failureReason,
        int attempts,
        Instant timestamp
) {}
