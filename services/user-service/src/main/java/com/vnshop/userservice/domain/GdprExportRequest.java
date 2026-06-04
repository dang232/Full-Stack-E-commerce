package com.vnshop.userservice.domain;

import java.time.Instant;
import java.util.*;

public class GdprExportRequest {
    private static final Set<String> REQUIRED_SERVICES = Set.of(
            "user-service", "order-service", "payment-service",
            "notification-service", "shipping-service");

    private final String requestId;
    private final String userId;
    private final Instant createdAt;
    private GdprExportStatus status;
    private final Map<String, String> fragments;
    private final Set<String> missingServices;
    private Instant completedAt;

    public GdprExportRequest(String userId) {
        this.requestId = UUID.randomUUID().toString();
        this.userId = userId;
        this.createdAt = Instant.now();
        this.status = GdprExportStatus.PENDING;
        this.fragments = new HashMap<>();
        this.missingServices = new HashSet<>(REQUIRED_SERVICES);
    }

    public GdprExportRequest(String requestId, String userId, Instant createdAt,
                             GdprExportStatus status, Map<String, String> fragments,
                             Set<String> missingServices, Instant completedAt) {
        this.requestId = requestId;
        this.userId = userId;
        this.createdAt = createdAt;
        this.status = status;
        this.fragments = new HashMap<>(fragments);
        this.missingServices = new HashSet<>(missingServices);
        this.completedAt = completedAt;
    }

    public void addFragment(String serviceName, String payload) {
        fragments.put(serviceName, payload);
        missingServices.remove(serviceName);
        if (missingServices.isEmpty()) {
            status = GdprExportStatus.COMPLETED;
            completedAt = Instant.now();
        } else {
            status = GdprExportStatus.IN_PROGRESS;
        }
    }

    public void markPartial() {
        status = GdprExportStatus.PARTIAL;
        completedAt = Instant.now();
    }

    public boolean isComplete() {
        return status == GdprExportStatus.COMPLETED || status == GdprExportStatus.PARTIAL;
    }

    public String getRequestId() { return requestId; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public GdprExportStatus getStatus() { return status; }
    public Map<String, String> getFragments() { return Map.copyOf(fragments); }
    public Set<String> getMissingServices() { return Set.copyOf(missingServices); }
    public Instant getCompletedAt() { return completedAt; }
}
