package com.vnshop.productservice.infrastructure.event;

import com.vnshop.productservice.domain.ProductEvent;
import com.vnshop.productservice.domain.port.out.ProductEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductEventPublisher implements ProductEventPublisherPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductEventPublisher.class);
    private static final String TOPIC = "product-events";

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;

    public ProductEventPublisher(KafkaTemplate<String, ProductEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(ProductEvent event) {
        LOGGER.info("Publishing product event {} for product {}", event.eventType(), event.productId());
        try {
            kafkaTemplate.send(TOPIC, event.productId(), event);
        } catch (RuntimeException exception) {
            LOGGER.warn("Product event logged but Kafka publish failed for product {}", event.productId(), exception);
        }
    }
}
