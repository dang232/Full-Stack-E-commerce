package com.vnshop.paymentservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookSpringDataRepository
        extends JpaRepository<ProcessedWebhookJpaEntity, ProcessedWebhookJpaEntity.ProcessedWebhookId> {

    boolean existsByWebhookIdAndProvider(String webhookId, String provider);
}
