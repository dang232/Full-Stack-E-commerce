package com.vnshop.paymentservice.infrastructure.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire format for the {@code payment.completed} Kafka topic. One event per
 * promoted payment, regardless of provider. Consumers (order-service) read
 * {@code orderId} and {@code status} to flip the order's payment status.
 *
 * <p>FX fields ({@code externalAmount}, {@code externalCurrency}, {@code fxRate},
 * {@code fxRateAt}) are nullable — they are populated only when the payment was
 * settled in a foreign currency and an exchange rate was captured at callback time.
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
        String callbackEventId,
        BigDecimal externalAmount,
        String externalCurrency,
        BigDecimal fxRate,
        Instant fxRateAt) {
}
