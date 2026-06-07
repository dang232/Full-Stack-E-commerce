package com.vnshop.userservice.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.userservice.domain.GdprDeletionServiceStatus;
import com.vnshop.userservice.domain.port.out.GdprDeletionStatusPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GdprDeletionCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(GdprDeletionCompletedListener.class);

    private final GdprDeletionStatusPort deletionStatusPort;
    private final ObjectMapper objectMapper;

    public GdprDeletionCompletedListener(GdprDeletionStatusPort deletionStatusPort,
                                         ObjectMapper objectMapper) {
        this.deletionStatusPort = deletionStatusPort;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "gdpr.deletion-completed", groupId = "user-service-gdpr-deletion")
    public void onDeletionCompleted(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String requestId = (String) event.get("requestId");
            String serviceName = (String) event.get("serviceName");
            String userId = (String) event.get("userId");
            boolean success = !event.containsKey("error");

            if (requestId == null) {
                log.warn("GDPR deletion-completed event missing requestId for userId={}", userId);
                return;
            }

            GdprDeletionServiceStatus status = success
                    ? GdprDeletionServiceStatus.COMPLETED
                    : GdprDeletionServiceStatus.FAILED;
            String errorMessage = success ? null : (String) event.get("error");

            deletionStatusPort.updateServiceStatus(requestId, serviceName, status, errorMessage);
            log.info("GDPR deletion status updated: requestId={}, service={}, status={}",
                    requestId, serviceName, status);
        } catch (Exception e) {
            log.error("Failed to process GDPR deletion-completed event", e);
        }
    }
}
