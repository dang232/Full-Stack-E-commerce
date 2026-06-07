package com.vnshop.userservice.infrastructure.config;

import com.vnshop.userservice.application.GdprDeleteUseCase;
import com.vnshop.userservice.application.GdprExportUseCase;
import com.vnshop.userservice.domain.port.out.GdprDeletionStatusPort;
import com.vnshop.userservice.domain.port.out.GdprExportRepositoryPort;
import com.vnshop.userservice.domain.port.out.UserRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class GdprUseCaseConfig {
    @Bean
    public GdprExportUseCase gdprExportUseCase(GdprExportRepositoryPort repository,
                                               KafkaTemplate<String, Object> kafkaTemplate) {
        return new GdprExportUseCase(repository, kafkaTemplate);
    }

    @Bean
    public GdprDeleteUseCase gdprDeleteUseCase(UserRepositoryPort userRepository,
                                               KafkaTemplate<String, Object> kafkaTemplate,
                                               GdprDeletionStatusPort deletionStatusPort) {
        return new GdprDeleteUseCase(userRepository, kafkaTemplate, deletionStatusPort);
    }
}
