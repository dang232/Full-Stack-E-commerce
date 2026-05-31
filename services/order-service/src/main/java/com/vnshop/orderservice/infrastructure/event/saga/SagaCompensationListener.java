package com.vnshop.orderservice.infrastructure.event.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.application.saga.SagaOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Listens for compensation-confirmation events from downstream services
 * (inventory, payment, shipping) and promotes COMPENSATING sagas to FAILED
 * without waiting for the timeout finalizer.
 *
 * <p>Downstream services must publish to {@code inventory.released},
 * {@code payment.refunded}, or {@code shipping.cancelled} with a JSON
 * payload containing a {@code sagaId} field for this listener to fire.
 */
@Service
public class SagaCompensationListener {
    private static final Logger LOG = LoggerFactory.getLogger(SagaCompensationListener.class);

    private final SagaOrchestrator sagaOrchestrator;
    private final ObjectMapper objectMapper;

    public SagaCompensationListener(SagaOrchestrator sagaOrchestrator, ObjectMapper objectMapper) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"inventory.released", "payment.refunded", "shipping.cancelled"},
            groupId = "order-service-saga-compensation"
    )
    public void onCompensationConfirmed(String eventJson,
                                        @org.springframework.messaging.handler.annotation.Header(name = "kafka_receivedTopic", required = false) String topic) {
        JsonNode envelope = readTree(eventJson);
        JsonNode payload = envelope.hasNonNull("payload") ? readTree(envelope.get("payload").asText()) : envelope;

        String sagaId = text(payload, "sagaId");
        if (sagaId == null || sagaId.isBlank()) {
            LOG.warn("Skipping compensation confirmation — missing sagaId in event from {}", topic);
            return;
        }

        sagaOrchestrator.onCompensationCompleted(sagaId, topic == null ? "UNKNOWN" : topic);
        LOG.info("Saga {} compensation confirmed by {}", sagaId, topic);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalArgumentException("compensation event payload is invalid", exception);
        }
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() ? null : value.asText();
    }
}
