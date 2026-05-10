package com.vnshop.paymentservice.infrastructure.gateway;

import java.time.Instant;

public record PaymentCallbackAttempt(
        String callbackId,
        String provider,
        String eventId,
        String payloadHash,
        String signatureHash,
        String headersJson,
        String bodyJson,
        Instant receivedAt,
        String processingStatus,
        boolean duplicateReplay
) {
}
