package com.vnshop.orderservice.infrastructure.event.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.PaymentStatus;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the loop on every successful payment callback. Payment-service's
 * {@code PaymentCallbackOutboxRelay} publishes a {@code payment.completed}
 * event whenever {@code PaymentPromotionService} promotes a payment to
 * COMPLETED — VNPay, MoMo, Stripe, PayPal, SePay, all of them. This listener
 * reads the event, flips the order's payment status, and emits an
 * {@code order.paid} event so the finance + projection listeners pick it up.
 *
 * <p>Idempotent: a second event for an already-COMPLETED order is a no-op
 * (no save, no event). Spring Kafka's at-least-once delivery means duplicates
 * are expected on consumer-group rebalance.
 */
@Service
public class PaymentCompletedListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCompletedListener.class);

    private final OrderRepositoryPort orderRepository;
    private final OrderEventPublisherPort orderEventPublisher;
    private final ObjectMapper objectMapper;

    public PaymentCompletedListener(
            OrderRepositoryPort orderRepository,
            OrderEventPublisherPort orderEventPublisher,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.completed", groupId = "order-service-payment")
    @Transactional
    public void onPaymentCompleted(String eventJson) {
        JsonNode payload = readTree(eventJson);
        String orderIdRaw = text(payload, "orderId");
        String status = text(payload, "status");
        if (orderIdRaw == null || orderIdRaw.isBlank()) {
            LOGGER.warn("payment.completed missing orderId — skipping. payload={}", eventJson);
            return;
        }
        if (status != null && !"COMPLETED".equals(status)) {
            LOGGER.debug("payment.completed orderId={} status={} — only COMPLETED triggers the order flip", orderIdRaw, status);
            return;
        }
        UUID orderId = parseOrderId(orderIdRaw);
        if (orderId == null) {
            LOGGER.warn("payment.completed orderId={} not a valid UUID — skipping", orderIdRaw);
            return;
        }
        Optional<Order> maybeOrder = orderRepository.findById(orderId);
        if (maybeOrder.isEmpty()) {
            LOGGER.warn("payment.completed orderId={} not found in order-service — skipping", orderId);
            return;
        }
        Order order = maybeOrder.get();
        if (order.paymentStatus() == PaymentStatus.COMPLETED) {
            LOGGER.debug("payment.completed orderId={} already COMPLETED — idempotent no-op", orderId);
            return;
        }
        order.markPaymentCompleted();
        String externalAmountRaw = text(payload, "externalAmount");
        if (externalAmountRaw != null) {
            order.setExternalAmount(new BigDecimal(externalAmountRaw));
            order.setExternalCurrency(text(payload, "externalCurrency"));
            String fxRateRaw = text(payload, "fxRate");
            order.setFxRate(fxRateRaw != null ? new BigDecimal(fxRateRaw) : null);
            String fxRateAtRaw = text(payload, "fxRateAt");
            order.setFxRateAt(fxRateAtRaw != null ? Instant.parse(fxRateAtRaw) : null);
        }
        Order saved = orderRepository.save(order);
        orderEventPublisher.publishOrderPaid(saved);
        LOGGER.info("payment-completed orderId={} provider={} transactionRef={}",
                orderId, text(payload, "provider"), text(payload, "transactionRef"));
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("payment.completed payload is not valid JSON", ex);
        }
    }

    private static UUID parseOrderId(String raw) {
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
}
