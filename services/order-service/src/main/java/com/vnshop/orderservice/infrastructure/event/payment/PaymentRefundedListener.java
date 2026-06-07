package com.vnshop.orderservice.infrastructure.event.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.ReturnStatus;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the buyer-visible half of the refund saga. Payment-service publishes
 * {@code payment.refunded} after a successful gateway refund call; this listener
 * reads the event and flips the underlying Return from COMPLETED to REFUNDED
 * so the buyer's order page reflects the money having moved.
 *
 * <p>Idempotent: a redelivery for an already-REFUNDED return is a no-op.
 */
@Service
public class PaymentRefundedListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentRefundedListener.class);

    private final ReturnRepositoryPort returnRepository;
    private final ObjectMapper objectMapper;

    public PaymentRefundedListener(ReturnRepositoryPort returnRepository, ObjectMapper objectMapper) {
        this.returnRepository = returnRepository;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "3",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = ".DLT",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(topics = "payment.refunded", groupId = "order-service-refund", concurrency = "6")
    @Transactional
    public void onPaymentRefunded(String eventJson) {
        JsonNode payload = readTree(eventJson);
        String returnIdRaw = text(payload, "returnId");
        if (returnIdRaw == null || returnIdRaw.isBlank()) {
            LOGGER.warn("payment.refunded missing returnId — skipping. payload={}", eventJson);
            return;
        }
        UUID returnId = parseUuid(returnIdRaw);
        if (returnId == null) {
            LOGGER.warn("payment.refunded returnId={} not a valid UUID — skipping", returnIdRaw);
            return;
        }
        Optional<Return> maybeReturn = returnRepository.findById(returnId);
        if (maybeReturn.isEmpty()) {
            LOGGER.warn("payment.refunded returnId={} not found — skipping", returnId);
            return;
        }
        Return orderReturn = maybeReturn.get();
        if (orderReturn.status() == ReturnStatus.REFUNDED) {
            LOGGER.debug("payment.refunded returnId={} already REFUNDED — idempotent no-op", returnId);
            return;
        }
        if (orderReturn.status() != ReturnStatus.COMPLETED) {
            LOGGER.warn("payment.refunded returnId={} in status {} — refund event ignored", returnId, orderReturn.status());
            return;
        }
        orderReturn.markRefunded();
        returnRepository.save(orderReturn);
        LOGGER.info("payment-refunded returnId={} orderId={} refundId={}",
                returnId, text(payload, "orderId"), text(payload, "refundId"));
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("payment.refunded payload is not valid JSON", ex);
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    @DltHandler
    public void handleDlt(String message) {
        LOGGER.error("Message sent to DLT after retries exhausted: {}", message);
    }
}
