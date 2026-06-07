package com.vnshop.orderservice.infrastructure.event.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.PaymentStatus;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Consumes {@code payment.chargeback.created} events published by payment-service
 * and flips the order's payment status to {@link PaymentStatus#DISPUTED}.
 *
 * <p>Idempotent: if the order is already DISPUTED the event is a no-op.
 */
@Service
public class ChargebackCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(ChargebackCreatedListener.class);

    private final OrderRepositoryPort orderRepository;
    private final ObjectMapper objectMapper;

    public ChargebackCreatedListener(OrderRepositoryPort orderRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "3",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = ".DLT",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(topics = "payment.chargeback.created", groupId = "order-service-chargeback", concurrency = "3")
    @Transactional
    public void onChargebackCreated(String eventJson) {
        JsonNode payload = readTree(eventJson);
        String orderIdRaw = text(payload, "orderId");
        if (orderIdRaw == null || orderIdRaw.isBlank() || "UNKNOWN".equals(orderIdRaw)) {
            log.warn("payment.chargeback.created missing orderId — skipping. payload={}", eventJson);
            return;
        }

        UUID orderId = parseUuid(orderIdRaw);
        if (orderId == null) {
            log.warn("payment.chargeback.created orderId={} not a valid UUID — skipping", orderIdRaw);
            return;
        }

        Optional<Order> maybeOrder = orderRepository.findById(orderId);
        if (maybeOrder.isEmpty()) {
            log.warn("payment.chargeback.created orderId={} not found — skipping", orderId);
            return;
        }

        Order order = maybeOrder.get();
        if (order.paymentStatus() == PaymentStatus.DISPUTED) {
            log.debug("payment.chargeback.created orderId={} already DISPUTED — idempotent no-op", orderId);
            return;
        }

        order.markPaymentDisputed();
        orderRepository.save(order);
        log.info("chargeback-disputed orderId={} chargebackId={} provider={}",
                orderId, text(payload, "chargebackId"), text(payload, "provider"));
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("payment.chargeback.created payload is not valid JSON", ex);
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("payment.chargeback.created sent to DLT after retries exhausted: {}", message);
    }
}
