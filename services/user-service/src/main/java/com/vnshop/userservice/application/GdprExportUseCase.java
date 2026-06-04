package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.GdprExportRequest;
import com.vnshop.userservice.domain.port.out.GdprExportRepositoryPort;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.Map;

public class GdprExportUseCase {
    private final GdprExportRepositoryPort repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public GdprExportUseCase(GdprExportRepositoryPort repository,
                             KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public String initiateExport(String userId) {
        if (repository.hasRecentExport(userId)) {
            throw new IllegalStateException("Export already requested within the last hour");
        }
        var request = new GdprExportRequest(userId);
        repository.save(request);
        kafkaTemplate.send("gdpr.export-requested", userId, Map.of(
                "userId", userId, "requestId", request.getRequestId()));
        return request.getRequestId();
    }

    public GdprExportRequest getExportStatus(String userId, String requestId) {
        return repository.findByRequestId(requestId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Export request not found"));
    }
}
