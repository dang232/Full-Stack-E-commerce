package com.vnshop.paymentservice.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PaymentCallbackOutboxSpringDataRepository extends JpaRepository<PaymentCallbackOutboxJpaEntity, Long> {
    List<PaymentCallbackOutboxJpaEntity> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    @Query("""
            SELECT e FROM PaymentCallbackOutboxJpaEntity e
            WHERE e.dead = FALSE
              AND e.publishedAt IS NULL
              AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= :now)
            ORDER BY e.createdAt ASC
            """)
    List<PaymentCallbackOutboxJpaEntity> findRetryable(@Param("now") Instant now, Pageable pageable);
}
