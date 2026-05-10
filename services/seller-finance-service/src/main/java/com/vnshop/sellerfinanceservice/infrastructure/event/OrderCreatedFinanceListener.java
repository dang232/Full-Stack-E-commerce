package com.vnshop.sellerfinanceservice.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.sellerfinanceservice.application.CreditWalletUseCase;
import com.vnshop.sellerfinanceservice.domain.CommissionTier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderCreatedFinanceListener {
    private final CreditWalletUseCase creditWalletUseCase;
    private final ObjectMapper objectMapper;

    public OrderCreatedFinanceListener(CreditWalletUseCase creditWalletUseCase, ObjectMapper objectMapper) {
        this.creditWalletUseCase = creditWalletUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order-events", groupId = "seller-finance-service")
    public void onOrderEvent(String eventJson) {
        JsonNode envelope = readTree(eventJson);
        String eventType = text(envelope, "eventType");
        JsonNode payload = envelope.hasNonNull("payload") ? readTree(envelope.get("payload").asText()) : envelope;
        if (!"ORDER_CREATED".equals(eventType) && !"ORDER_CREATED".equals(text(payload, "eventType"))) {
            return;
        }
        for (JsonNode sellerTotal : payload.path("sellerTotals")) {
            String sellerId = text(sellerTotal, "sellerId");
            BigDecimal orderAmount = sellerTotal.path("amount").decimalValue();
            CommissionTier tier = CommissionTier.valueOf(textOrDefault(sellerTotal, "commissionTier", CommissionTier.STANDARD.name()));
            creditWalletUseCase.credit(sellerId, orderAmount, tier);
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
