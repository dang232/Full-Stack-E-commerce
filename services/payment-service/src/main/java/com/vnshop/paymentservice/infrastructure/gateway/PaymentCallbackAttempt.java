package com.vnshop.paymentservice.infrastructure.gateway;

import java.time.Instant;
import java.util.UUID;

public record PaymentCallbackAttempt(
        UUID callbackId,
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
