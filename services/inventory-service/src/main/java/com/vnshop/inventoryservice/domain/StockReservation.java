package com.vnshop.inventoryservice.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain record for a single line item reserved out of {@link StockLevel} for
 * an order. A reservation is created in {@code RESERVED} status by the gRPC
 * Reserve handler and transitions to {@code RELEASED} when the order is
 * cancelled or the saga compensates.
 */
public record StockReservation(
        UUID reservationId,
        String orderId,
        String productId,
        String variant,
        int quantity,
        Status status,
        Instant createdAt,
        Instant releasedAt
) {
    public enum Status {
        RESERVED,
        RELEASED
    }

    public StockReservation released(Instant at) {
        return new StockReservation(reservationId, orderId, productId, variant,
                quantity, Status.RELEASED, createdAt, at);
    }
}
