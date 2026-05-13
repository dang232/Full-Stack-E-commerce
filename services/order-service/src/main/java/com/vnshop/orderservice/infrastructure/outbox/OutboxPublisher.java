package com.vnshop.orderservice.infrastructure.outbox;

import io.opentelemetry.api.trace.Span;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String TOPIC = "order-events";

    private final OutboxEventRepository repository;
    private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;
    private final int batchSize;

    public OutboxPublisher(
            OutboxEventRepository repository,
            ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider,
            @Value("${outbox.publisher.batch-size:50}") int batchSize
    ) {
        this.repository = repository;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            return;
        }

        List<OutboxEventJpaEntity> events = repository.findByStatusOrderByCreatedAt(
                OutboxEvent.Status.PENDING,
                PageRequest.of(0, batchSize)
        );
        for (OutboxEventJpaEntity event : events) {
            publishEvent(kafkaTemplate, event);
        }
    }

    private void publishEvent(KafkaTemplate<String, Object> kafkaTemplate, OutboxEventJpaEntity event) {
        try {
            ProducerRecord<String, Object> record = new ProducerRecord<>(TOPIC, event.getAggregateId(), event.toDomain());
            record.headers().add("traceparent", ("00-" + Span.current().getSpanContext().getTraceId() + "-" + Span.current().getSpanContext().getSpanId() + "-01").getBytes());
            kafkaTemplate.send(record);
            event.markPublished();
        } catch (RuntimeException exception) {
            LOGGER.warn("Outbox publish failed for event {} aggregate {}", event.getId(), event.getAggregateId(), exception);
        }
    }
}
