package com.vnshop.orderservice.domain.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Port for updating read-model projections from application layer.
 * Decouples projector logic from JPA entity specifics.
 */
public interface ProjectionPort {
    void upsertOrderSummary(String orderId, String buyerId, String status,
                            BigDecimal totalAmount, int itemCount, Instant createdAt);
}
