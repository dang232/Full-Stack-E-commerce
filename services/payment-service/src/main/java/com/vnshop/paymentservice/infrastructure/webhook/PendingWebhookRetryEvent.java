package com.vnshop.paymentservice.infrastructure.webhook;

import java.util.UUID;

/**
 * Published by {@link WebhookRetryScheduler} when a pending webhook entry is due
 * for retry. Listeners re-process the original payload in their own transaction.
 *
 * <p>The event is intentionally a plain record (not a Spring {@code ApplicationEvent}
 * subclass) so listeners stay decoupled from the scheduler implementation.
 */
public record PendingWebhookRetryEvent(
        UUID pendingId,
        String webhookId,
        String provider,
        String eventType,
        String payload) {
}
