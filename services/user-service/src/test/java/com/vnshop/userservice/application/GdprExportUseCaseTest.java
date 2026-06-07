package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.GdprExportRequest;
import com.vnshop.userservice.domain.GdprExportStatus;
import com.vnshop.userservice.domain.port.out.GdprExportRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdprExportUseCaseTest {

    private GdprExportRepositoryPort repository;
    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, Object> kafkaTemplate;
    private GdprExportUseCase useCase;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(GdprExportRepositoryPort.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        useCase = new GdprExportUseCase(repository, kafkaTemplate);
    }

    @Test
    void initiateExport_noRecentExport_savesAndPublishes() {
        when(repository.hasRecentExport("user-123")).thenReturn(false);

        String requestId = useCase.initiateExport("user-123");

        assertThat(requestId).isNotNull();
        verify(repository).save(any(GdprExportRequest.class));
        verify(kafkaTemplate).send(eq("gdpr.export-requested"), eq("user-123"), any());
    }

    @Test
    void initiateExport_recentExportExists_throwsIllegalStateException() {
        when(repository.hasRecentExport("user-123")).thenReturn(true);

        assertThatThrownBy(() -> useCase.initiateExport("user-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already requested");

        verify(repository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void getExportStatus_requestExistsForUser_returnsRequest() {
        GdprExportRequest stored = new GdprExportRequest(
                "req-1", "user-123", Instant.now(),
                GdprExportStatus.PENDING, new HashMap<>(), new HashSet<>(), null);
        when(repository.findByRequestId("req-1")).thenReturn(Optional.of(stored));

        GdprExportRequest result = useCase.getExportStatus("user-123", "req-1");

        assertThat(result.getRequestId()).isEqualTo("req-1");
        assertThat(result.getUserId()).isEqualTo("user-123");
    }

    @Test
    void getExportStatus_requestBelongsToDifferentUser_throwsIllegalArgumentException() {
        GdprExportRequest stored = new GdprExportRequest(
                "req-1", "other-user", Instant.now(),
                GdprExportStatus.PENDING, new HashMap<>(), new HashSet<>(), null);
        when(repository.findByRequestId("req-1")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> useCase.getExportStatus("user-123", "req-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getExportStatus_requestNotFound_throwsIllegalArgumentException() {
        when(repository.findByRequestId("req-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getExportStatus("user-123", "req-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
