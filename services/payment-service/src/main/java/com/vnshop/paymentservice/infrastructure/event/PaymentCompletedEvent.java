package com.vnshop.paymentservice.infrastructure.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Wire format for the {@code payment.completed} Kafka topic. One event per
 * promoted payment, regardless of provider. Consumers (order-service) read
 * {@code orderId} and {@code status} to flip the order's payment status.
 */
public record PaymentCompletedEvent(
        String provider,
        UUID paymentId,
        String orderId,
        String transactionRef,
        String status,
        BigDecimal amount,
        String currency,
        UUID callbackId,
        String callbackEventId) {
}
