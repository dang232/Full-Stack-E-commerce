package com.vnshop.orderservice.domain.port.out;

/**
 * Port for publishing domain events via the transactional outbox.
 * Decouples saga/application logic from JPA outbox entity details.
 */
public interface OutboxPort {
    void publish(String aggregateType, String aggregateId, String eventType, String payload);
}
