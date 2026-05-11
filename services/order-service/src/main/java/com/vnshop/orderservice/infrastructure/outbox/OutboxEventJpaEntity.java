package com.vnshop.orderservice.infrastructure.outbox;

import com.vnshop.orderservice.infrastructure.persistence.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "outbox_events", schema = "order_svc")
@Getter
@Setter
public class OutboxEventJpaEntity extends BaseJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEvent.Status status;

    protected OutboxEventJpaEntity() {
    }

    private OutboxEventJpaEntity(
            Long id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            OutboxEvent.Status status
    ) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
    }

    public static OutboxEventJpaEntity fromDomain(OutboxEvent event) {
        return new OutboxEventJpaEntity(
                event.id(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.payload(),
                event.status()
        );
    }

    public OutboxEvent toDomain() {
        return new OutboxEvent(id, aggregateType, aggregateId, eventType, payload, status, getCreatedAt());
    }


    public void markPublished() {
        status = OutboxEvent.Status.PUBLISHED;
    }
}
