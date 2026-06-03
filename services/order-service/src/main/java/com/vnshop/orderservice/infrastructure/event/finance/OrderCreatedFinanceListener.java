package com.vnshop.orderservice.infrastructure.event.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.application.finance.CreditWalletUseCase;
import com.vnshop.orderservice.domain.CommissionTier;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

@Service
public class OrderCreatedFinanceListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderCreatedFinanceListener.class);

    private final CreditWalletUseCase creditWalletUseCase;
    private final ObjectMapper objectMapper;

    public OrderCreatedFinanceListener(CreditWalletUseCase creditWalletUseCase, ObjectMapper objectMapper) {
        this.creditWalletUseCase = creditWalletUseCase;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = ".DLT",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(topics = {"order.created", "order.paid"}, groupId = "order-service-finance")
    public void onOrderEvent(String eventJson) {
        JsonNode envelope = readTree(eventJson);
        JsonNode payload = envelope.hasNonNull("payload") ? readTree(envelope.get("payload").asText()) : envelope;
        String eventType = textOrDefault(payload, "eventType", text(envelope, "eventType"));
        if (!"ORDER_CREATED".equals(eventType) && !"ORDER_PAID".equals(eventType)) {
            return;
        }
        for (JsonNode sellerTotal : payload.path("sellerTotals")) {
            String sellerId = text(sellerTotal, "sellerId");
            BigDecimal originalAmount = sellerTotal.path("originalAmount").isMissingNode()
                    ? sellerTotal.path("amount").decimalValue()
                    : sellerTotal.path("originalAmount").decimalValue();
            CommissionTier tier = CommissionTier.valueOf(textOrDefault(sellerTotal, "commissionTier", CommissionTier.STANDARD.name()));
            String idempotencyKey = eventType + ":" + text(payload, "orderId") + ":" + sellerId;
            creditWalletUseCase.credit(sellerId, originalAmount, tier, idempotencyKey);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalArgumentException("order event payload is invalid", exception);
        }
    }

    private static String text(JsonNode node, String fieldName) {
        return node.path(fieldName).asText();
    }

    private static String textOrDefault(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.asText().isBlank() ? defaultValue : value.asText();
    }

    @DltHandler
    public void handleDlt(String message) {
        LOGGER.error("Message sent to DLT after retries exhausted: {}", message);
    }
}
