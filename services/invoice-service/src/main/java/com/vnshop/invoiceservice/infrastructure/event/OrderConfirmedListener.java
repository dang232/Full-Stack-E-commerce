package com.vnshop.invoiceservice.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.invoiceservice.application.InvoiceService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.stereotype.Service;

/**
 * Consumes {@code order.confirmed} events and creates a DRAFT Invoice record.
 *
 * <p>Idempotent: duplicate events for the same orderId are silently ignored
 * by {@link InvoiceService#createDraftInvoice}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderConfirmedListener {

    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = ".DLT",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(topics = "order.confirmed", groupId = "invoice-service", concurrency = "3")
    public void onOrderConfirmed(String eventJson) {
        JsonNode payload = parseJson(eventJson);
        if (payload == null) {
            return;
        }

        String orderIdRaw = text(payload, "orderId");
        if (orderIdRaw == null || orderIdRaw.isBlank()) {
            log.warn("order.confirmed missing orderId — skipping. payload={}", eventJson);
            return;
        }

        UUID orderId = parseUuid(orderIdRaw);
        if (orderId == null) {
            log.warn("order.confirmed orderId={} is not a valid UUID — skipping", orderIdRaw);
            return;
        }

        String sellerId = text(payload, "sellerId");
        String items = extractJsonField(payload, "items");
        String vatBreakdown = extractJsonField(payload, "vatBreakdown");
        String buyerTaxCode = text(payload, "buyerTaxCode");

        invoiceService.createDraftInvoice(orderId, sellerId, items, vatBreakdown, buyerTaxCode);
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("order.confirmed message sent to DLT after retries exhausted: {}", message);
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            log.error("order.confirmed payload is not valid JSON — skipping: {}", ex.getMessage());
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String extractJsonField(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Failed to serialize field '{}': {}", field, ex.getMessage());
            return null;
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
