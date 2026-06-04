package com.vnshop.orderservice.domain.port.out;

import java.math.BigDecimal;

/**
 * Port for updating read-model projections from application layer.
 * Decouples projector logic from JPA entity specifics.
 */
public interface ProjectionPort {
    void upsertOrderSummary(String orderId, String status, String buyerId, String sellerId,
                            BigDecimal totalAmount, int itemCount);
}
