package com.vnshop.orderservice.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class GdprEventListener {
    private static final Logger log = LoggerFactory.getLogger(GdprEventListener.class);
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public GdprEventListener(JdbcTemplate jdbcTemplate,
                             KafkaTemplate<String, Object> kafkaTemplate,
                             ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "gdpr.export-requested", groupId = "order-service-gdpr")
    public void onExportRequested(String message) {
        try {
            Map<String, String> event = objectMapper.readValue(message, Map.class);
            String userId = event.get("userId");
            String requestId = event.get("requestId");
            List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                    "SELECT order_id, status, total_amount, currency, created_at FROM orders WHERE user_id = ?",
                    userId);
            String payload = objectMapper.writeValueAsString(Map.of("orders", orders));
            kafkaTemplate.send("gdpr.export-fragment", requestId, objectMapper.writeValueAsString(
                    Map.of("requestId", requestId, "serviceName", "order-service", "payload", payload)));
            log.info("GDPR export fragment published: requestId={}, orders={}", requestId, orders.size());
        } catch (Exception e) {
            log.error("Failed to process GDPR export request", e);
        }
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "gdpr.deletion-requested", groupId = "order-service-gdpr")
    public void onDeletionRequested(String message) {
        try {
            Map<String, String> event = objectMapper.readValue(message, Map.class);
            String userId = event.get("userId");
            int updated = jdbcTemplate.update(
                    "UPDATE orders SET shipping_address_line1='[REDACTED]', shipping_address_city='[REDACTED]', billing_name='[REDACTED]' WHERE user_id=?",
                    userId);
            kafkaTemplate.send("gdpr.deletion-completed", userId, objectMapper.writeValueAsString(
                    Map.of("serviceName", "order-service", "userId", userId, "recordsAnonymized", updated)));
            log.info("GDPR deletion: userId={}, ordersAnonymized={}", userId, updated);
        } catch (Exception e) {
            log.error("Failed to process GDPR deletion", e);
        }
    }
}
