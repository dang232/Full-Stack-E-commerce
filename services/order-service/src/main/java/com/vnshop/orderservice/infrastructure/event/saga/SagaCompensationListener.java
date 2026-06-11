package com.vnshop.orderservice.infrastructure.event.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.application.saga.SagaOrchestrator;
import com.vnshop.orderservice.domain.port.out.SagaStateRepository;
import com.vnshop.orderservice.domain.saga.SagaState;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.stereotype.Service;

/**
 * Listens for compensation-confirmation events from downstream services
 * (inventory, payment, shipping) and promotes COMPENSATING sagas to FAILED
 * without waiting for the timeout finalizer.
 *
 * <p>Downstream services publish to {@code inventory.released},
 * {@code payment.refunded}, or {@code shipping.cancelled}. The payload is
 * expected to contain either a {@code sagaId} field (preferred) or an
 * {@code orderId} field. When only {@code orderId} is present the saga is
 * looked up by order, allowing downstream services that are unaware of the
 * saga ID to still close the compensation loop.
 */
@Service
public class SagaCompensationListener {
    private static final Logger LOG = LoggerFactory.getLogger(SagaCompensationListener.class);

    private final SagaOrchestrator sagaOrchestrator;
    private final SagaStateRepository sagaStateRepository;
    private final ObjectMapper objectMapper;

    public SagaCompensationListener(
            SagaOrchestrator sagaOrchestrator,
            SagaStateRepository sagaStateRepository,
            ObjectMapper objectMapper) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.sagaStateRepository = sagaStateRepository;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "4",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = ".DLT",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = {"inventory.released", "payment.refunded", "shipping.cancelled"},
            groupId = "order-service-saga-compensation",
            concurrency = "6"
    )
    public void onCompensationConfirmed(String eventJson,
                                        @org.springframework.messaging.handler.annotation.Header(name = "kafka_receivedTopic", required = false) String topic) {
        JsonNode envelope = readTree(eventJson);
        JsonNode payload = envelope.hasNonNull("payload") ? readTree(envelope.get("payload").asText()) : envelope;

        String resolvedTopic = topic == null ? "UNKNOWN" : topic;

        // Prefer sagaId from payload; fall back to orderId lookup
        String sagaId = text(payload, "sagaId");
        if (sagaId == null || sagaId.isBlank()) {
            String orderId = text(payload, "orderId");
            if (orderId == null || orderId.isBlank()) {
                LOG.warn("Skipping compensation confirmation — missing both sagaId and orderId in event from {}", resolvedTopic);
                return;
            }
            Optional<SagaState> saga = sagaStateRepository.findByOrderId(orderId);
            if (saga.isEmpty()) {
                LOG.warn("Skipping compensation confirmation — no saga found for orderId={} from {}", orderId, resolvedTopic);
                return;
            }
            sagaId = saga.get().sagaId();
            LOG.debug("Resolved sagaId={} from orderId={} for compensation confirmation from {}", sagaId, orderId, resolvedTopic);
        }

        sagaOrchestrator.onCompensationCompleted(sagaId, resolvedTopic);
        LOG.info("Saga {} compensation confirmed by {}", sagaId, resolvedTopic);
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
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    @DltHandler
    public void handleDlt(String message) {
        LOG.error("CRITICAL: Saga compensation message sent to DLT — manual intervention required: {}", message);
    }
}
