package com.vnshop.userservice.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.userservice.domain.port.out.GdprExportRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class GdprExportFragmentListener {
    private static final Logger log = LoggerFactory.getLogger(GdprExportFragmentListener.class);
    private final GdprExportRepositoryPort repository;
    private final ObjectMapper objectMapper;

    public GdprExportFragmentListener(GdprExportRepositoryPort repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "gdpr.export-fragment", groupId = "user-service-gdpr-export")
    public void onExportFragment(String message) {
        try {
            Map<String, String> event = objectMapper.readValue(message, Map.class);
            String requestId = event.get("requestId");
            String serviceName = event.get("serviceName");
            String payload = event.get("payload");
            repository.findByRequestId(requestId).ifPresent(request -> {
                request.addFragment(serviceName, payload);
                repository.save(request);
                log.info("GDPR export fragment: requestId={}, service={}, complete={}",
                        requestId, serviceName, request.isComplete());
            });
        } catch (Exception e) {
            log.error("Failed to process GDPR export fragment", e);
        }
    }
}
