package com.vnshop.orderservice.infrastructure.event.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.application.finance.CreditWalletUseCase;
import com.vnshop.orderservice.domain.finance.CommissionTier;
import java.math.BigDecimal;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderCreatedFinanceListener {
    private final CreditWalletUseCase creditWalletUseCase;
    private final ObjectMapper objectMapper;

    public OrderCreatedFinanceListener(CreditWalletUseCase creditWalletUseCase, ObjectMapper objectMapper) {
        this.creditWalletUseCase = creditWalletUseCase;
        this.objectMapper = objectMapper;
    }

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
}
