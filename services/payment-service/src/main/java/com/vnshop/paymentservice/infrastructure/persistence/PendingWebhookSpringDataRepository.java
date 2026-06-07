package com.vnshop.paymentservice.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PendingWebhookSpringDataRepository extends JpaRepository<PendingWebhookJpaEntity, UUID> {

    @Query("""
            SELECT e FROM PendingWebhookJpaEntity e
            WHERE e.status = 'PENDING'
              AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)
            ORDER BY e.createdAt ASC
            """)
    List<PendingWebhookJpaEntity> findRetryable(@Param("now") Instant now, Pageable pageable);
}
