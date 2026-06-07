package com.vnshop.paymentservice.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface WebhookDeadLetterSpringDataRepository extends JpaRepository<WebhookDeadLetterJpaEntity, UUID> {
    Page<WebhookDeadLetterJpaEntity> findAll(Pageable pageable);
}
