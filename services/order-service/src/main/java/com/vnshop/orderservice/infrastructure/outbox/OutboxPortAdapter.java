package com.vnshop.orderservice.infrastructure.outbox;

import com.vnshop.orderservice.domain.port.out.OutboxPort;
import org.springframework.stereotype.Component;

@Component
public class OutboxPortAdapter implements OutboxPort {
    private final OutboxEventRepository outboxEventRepository;

    public OutboxPortAdapter(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    public void publish(String aggregateType, String aggregateId, String eventType, String payload) {
        outboxEventRepository.save(OutboxEventJpaEntity.fromDomain(
            OutboxEvent.pending(aggregateType, aggregateId, eventType, payload)
        ));
    }
}
