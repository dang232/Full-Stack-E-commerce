package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.BuyerProfile;
import com.vnshop.userservice.domain.port.out.GdprDeletionStatusPort;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdprDeleteUseCaseTest {

    private UserRepositoryPort userRepository;
    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, Object> kafkaTemplate;
    private GdprDeletionStatusPort deletionStatusPort;
    private GdprDeleteUseCase useCase;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepositoryPort.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        deletionStatusPort = mock(GdprDeletionStatusPort.class);
        useCase = new GdprDeleteUseCase(userRepository, kafkaTemplate, deletionStatusPort);
    }

    @Test
    void initiateDelete_userExists_initiatesFullFlow() {
        BuyerProfile buyer = mock(BuyerProfile.class);
        when(buyer.keycloakId()).thenReturn("user-123");
        when(userRepository.findBuyerByKeycloakId("user-123")).thenReturn(Optional.of(buyer));

        String requestId = useCase.initiateDelete("user-123");

        assertThat(requestId).isNotNull();

        ArgumentCaptor<List<String>> servicesCaptor = ArgumentCaptor.captor();
        verify(deletionStatusPort).initializeServiceStatuses(eq(requestId), eq("user-123"), servicesCaptor.capture());
        assertThat(servicesCaptor.getValue()).containsExactlyInAnyOrderElementsOf(GdprDeleteUseCase.DOWNSTREAM_SERVICES);

        verify(kafkaTemplate).send(eq("gdpr.deletion-requested"), eq("user-123"), any());
        verify(userRepository).anonymize("user-123");
    }

    @Test
    void initiateDelete_userNotFound_throwsIllegalArgumentException() {
        when(userRepository.findBuyerByKeycloakId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.initiateDelete("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        verify(deletionStatusPort, never()).initializeServiceStatuses(anyString(), anyString(), anyList());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(userRepository, never()).anonymize(anyString());
    }

    @Test
    void downstreamServices_containsExpectedServices() {
        assertThat(GdprDeleteUseCase.DOWNSTREAM_SERVICES)
                .containsExactlyInAnyOrder("order-service", "payment-service", "shipping-service");
    }
}
