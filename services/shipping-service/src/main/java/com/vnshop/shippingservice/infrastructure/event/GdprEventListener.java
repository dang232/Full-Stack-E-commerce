package com.vnshop.shippingservice.infrastructure.event;

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
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public GdprEventListener(JdbcTemplate jdbcTemplate,
                             KafkaTemplate<String, String> kafkaTemplate,
                             ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "gdpr.export-requested", groupId = "shipping-service-gdpr")
    public void onExportRequested(String message) {
        try {
            Map<String, String> event = objectMapper.readValue(message, Map.class);
            String userId = event.get("userId");
            String requestId = event.get("requestId");
            List<Map<String, Object>> shipments = jdbcTemplate.queryForList(
                    "SELECT shipment_id, order_id, carrier, tracking_number, status, created_at FROM shipments WHERE user_id = ?",
                    userId);
            String payload = objectMapper.writeValueAsString(Map.of("shipments", shipments));
            kafkaTemplate.send("gdpr.export-fragment", requestId, objectMapper.writeValueAsString(
                    Map.of("requestId", requestId, "serviceName", "shipping-service", "payload", payload)));
            log.info("GDPR export fragment published: requestId={}, shipments={}", requestId, shipments.size());
        } catch (Exception e) {
            log.error("Failed to process GDPR export request", e);
        }
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "gdpr.deletion-requested", groupId = "shipping-service-gdpr")
    public void onDeletionRequested(String message) {
        try {
            Map<String, String> event = objectMapper.readValue(message, Map.class);
            String userId = event.get("userId");
            int updated = jdbcTemplate.update(
                    "UPDATE shipments SET delivery_address_line1='[REDACTED]', delivery_address_city='[REDACTED]', recipient_name='[REDACTED]' WHERE user_id=?",
                    userId);
            kafkaTemplate.send("gdpr.deletion-completed", userId, objectMapper.writeValueAsString(
                    Map.of("serviceName", "shipping-service", "userId", userId, "recordsAnonymized", updated)));
            log.info("GDPR deletion: userId={}, shipmentsAnonymized={}", userId, updated);
        } catch (Exception e) {
            log.error("Failed to process GDPR deletion", e);
        }
    }
}
