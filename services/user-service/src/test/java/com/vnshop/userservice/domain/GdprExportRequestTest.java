package com.vnshop.userservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GdprExportRequestTest {

    @Test
    void newRequest_hasPendingStatusAndAllServicesRequired() {
        var request = new GdprExportRequest("user-123");

        assertThat(request.getRequestId()).isNotNull();
        assertThat(request.getUserId()).isEqualTo("user-123");
        assertThat(request.getStatus()).isEqualTo(GdprExportStatus.PENDING);
        assertThat(request.getMissingServices()).containsExactlyInAnyOrder(
                "user-service", "order-service", "payment-service",
                "notification-service", "shipping-service");
        assertThat(request.getFragments()).isEmpty();
        assertThat(request.getCompletedAt()).isNull();
        assertThat(request.isComplete()).isFalse();
    }

    @Test
    void addFragment_transitionsToInProgress() {
        var request = new GdprExportRequest("user-123");

        request.addFragment("user-service", "{\"data\":\"ok\"}");

        assertThat(request.getStatus()).isEqualTo(GdprExportStatus.IN_PROGRESS);
        assertThat(request.getMissingServices()).doesNotContain("user-service");
        assertThat(request.getFragments()).containsKey("user-service");
        assertThat(request.isComplete()).isFalse();
    }

    @Test
    void addFragment_allServices_transitionsToCompleted() {
        var request = new GdprExportRequest("user-123");

        request.addFragment("user-service", "{}");
        request.addFragment("order-service", "{}");
        request.addFragment("payment-service", "{}");
        request.addFragment("notification-service", "{}");
        request.addFragment("shipping-service", "{}");

        assertThat(request.getStatus()).isEqualTo(GdprExportStatus.COMPLETED);
        assertThat(request.getMissingServices()).isEmpty();
        assertThat(request.getCompletedAt()).isNotNull();
        assertThat(request.isComplete()).isTrue();
    }

    @Test
    void markPartial_setsPartialStatusAndCompletedAt() {
        var request = new GdprExportRequest("user-123");
        request.addFragment("user-service", "{}");

        request.markPartial();

        assertThat(request.getStatus()).isEqualTo(GdprExportStatus.PARTIAL);
        assertThat(request.getCompletedAt()).isNotNull();
        assertThat(request.isComplete()).isTrue();
    }

    @Test
    void reconstructionConstructor_preservesAllFields() {
        Instant created = Instant.now().minusSeconds(60);
        Instant completed = Instant.now();
        Map<String, String> fragments = new HashMap<>(Map.of("user-service", "{}"));
        Set<String> missing = new HashSet<>(Set.of("order-service"));

        var request = new GdprExportRequest(
                "req-id", "user-123", created,
                GdprExportStatus.IN_PROGRESS, fragments, missing, completed);

        assertThat(request.getRequestId()).isEqualTo("req-id");
        assertThat(request.getUserId()).isEqualTo("user-123");
        assertThat(request.getCreatedAt()).isEqualTo(created);
        assertThat(request.getStatus()).isEqualTo(GdprExportStatus.IN_PROGRESS);
        assertThat(request.getFragments()).containsKey("user-service");
        assertThat(request.getMissingServices()).containsExactly("order-service");
        assertThat(request.getCompletedAt()).isEqualTo(completed);
    }
}
