package com.vnshop.paymentservice.infrastructure.gateway;

import java.util.List;

public interface PaymentCallbackOutbox {
    PaymentCallbackOutboxRecord save(PaymentCallbackOutboxRecord record);

    /**
     * Returns up to {@code limit} rows that have never been published, oldest first.
     * Drives the relay's batch-publish loop.
     */
    List<PaymentCallbackOutboxRecord> findUnpublished(int limit);

    /**
     * Stamps {@code published_at = now()} on the row with the given id, idempotently
     * (no-op if already published or absent).
     */
    void markPublished(Long id);
}
