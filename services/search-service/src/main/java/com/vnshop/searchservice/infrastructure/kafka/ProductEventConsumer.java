package com.vnshop.searchservice.infrastructure.kafka;

import com.vnshop.searchservice.infrastructure.idempotency.ProcessedEvent;
import com.vnshop.searchservice.infrastructure.idempotency.ProcessedEventRepository;
import com.vnshop.searchservice.infrastructure.persistence.ProductReadModelJpaEntity;
import com.vnshop.searchservice.infrastructure.persistence.ProductReadModelRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class ProductEventConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductEventConsumer.class);

    private final ProductReadModelRepository productReadModelRepository;
    private final ProcessedEventRepository processedEventRepository;

    public ProductEventConsumer(ProductReadModelRepository productReadModelRepository, ProcessedEventRepository processedEventRepository) {
        this.productReadModelRepository = productReadModelRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    @KafkaListener(topics = "product-events", groupId = "search-service")
    public void consume(ProductEvent event) {
        String eventId = event.deduplicationId();
        if (processedEventRepository.existsById(eventId)) {
            LOGGER.info("Skipping duplicate product event {} for product {}", event.eventType(), event.productId());
            return;
        }

        LOGGER.info("Consuming product event {} for product {}", event.eventType(), event.productId());
        if (event.eventType() == ProductEvent.EventType.DELETED) {
            productReadModelRepository.deleteById(event.productId());
        } else {
            productReadModelRepository.save(ProductReadModelJpaEntity.fromEvent(event.productId(), event.payload()));
        }
        processedEventRepository.save(new ProcessedEvent(eventId, event.eventType().name(), Instant.now()));
    }

    public record ProductEvent(String productId, EventType eventType, Instant timestamp, Map<String, Object> payload, String eventId) {
        public ProductEvent {
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }

        public String deduplicationId() {
            return eventId == null || eventId.isBlank() ? productId + ":" + eventType + ":" + timestamp : eventId;
        }

        public enum EventType {
            CREATED,
            UPDATED,
            DELETED,
            STOCK_CHANGED
        }
    }
}
