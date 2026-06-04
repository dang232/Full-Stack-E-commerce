package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import org.springframework.kafka.core.KafkaTemplate;
import java.time.Instant;
import java.util.Map;

public class GdprDeleteUseCase {
    private final UserRepositoryPort userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public GdprDeleteUseCase(UserRepositoryPort userRepository,
                             KafkaTemplate<String, Object> kafkaTemplate) {
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void initiateDelete(String userId) {
        userRepository.findBuyerByKeycloakId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        kafkaTemplate.send("gdpr.deletion-requested", userId, Map.of(
                "userId", userId, "requestedAt", Instant.now().toString()));
        userRepository.anonymize(userId);
    }
}
