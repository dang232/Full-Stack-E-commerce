package com.vnshop.paymentservice.application.webhook;

import java.time.Instant;

/**
 * Event published to {@code notification.alert} when a webhook lands in the DLT.
 * Downstream notification service handles actual Slack/email delivery.
 */
public record WebhookAlertEvent(
        String webhookId,
        String provider,
        String failureReason,
        Instant timestamp,
        String alertType
) {
    public static WebhookAlertEvent fromDlt(WebhookDltEvent dlt) {
        return new WebhookAlertEvent(
                dlt.webhookId(),
                dlt.provider(),
                dlt.failureReason(),
                dlt.timestamp(),
                "WEBHOOK_DLT"
        );
    }
}
