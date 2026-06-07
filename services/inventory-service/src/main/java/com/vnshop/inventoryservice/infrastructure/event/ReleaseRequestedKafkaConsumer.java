package com.vnshop.inventoryservice.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.inventoryservice.application.ReleaseStockUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code inventory.release-requested} events published by the order-service
 * when the inventory gRPC circuit breaker is open. Delegates to
 * {@link ReleaseStockUseCase} so compensation is guaranteed even when the
 * synchronous gRPC path is unavailable.
 */
@Component
public class ReleaseRequestedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReleaseRequestedKafkaConsumer.class);

    private final ReleaseStockUseCase releaseStockUseCase;
    private final ObjectMapper objectMapper;

    public ReleaseRequestedKafkaConsumer(ReleaseStockUseCase releaseStockUseCase, ObjectMapper objectMapper) {
        this.releaseStockUseCase = releaseStockUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory.release-requested", groupId = "inventory-svc-release")
    public void onReleaseRequested(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String orderId = node.get("orderId").asText();
            log.info("Received inventory.release-requested for orderId={}", orderId);
            releaseStockUseCase.release(orderId);
        } catch (Exception e) {
            log.error("Failed to process inventory.release-requested message: {}", message, e);
            throw new RuntimeException("inventory.release-requested processing failed", e);
        }
    }
}
