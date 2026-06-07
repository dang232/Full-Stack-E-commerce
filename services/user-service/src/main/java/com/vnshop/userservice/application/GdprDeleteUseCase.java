package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.port.out.GdprDeletionStatusPort;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GdprDeleteUseCase {

    static final List<String> DOWNSTREAM_SERVICES =
            List.of("order-service", "payment-service", "shipping-service");

    private final UserRepositoryPort userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final GdprDeletionStatusPort deletionStatusPort;

    public GdprDeleteUseCase(UserRepositoryPort userRepository,
                             KafkaTemplate<String, Object> kafkaTemplate,
                             GdprDeletionStatusPort deletionStatusPort) {
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.deletionStatusPort = deletionStatusPort;
    }

    public String initiateDelete(String userId) {
        userRepository.findBuyerByKeycloakId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String requestId = UUID.randomUUID().toString();
        deletionStatusPort.initializeServiceStatuses(requestId, userId, DOWNSTREAM_SERVICES);

        kafkaTemplate.send("gdpr.deletion-requested", userId, Map.of(
                "userId", userId,
                "requestId", requestId,
                "requestedAt", Instant.now().toString()));

        userRepository.anonymize(userId);
        return requestId;
    }
}
