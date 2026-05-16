package com.vnshop.orderservice.infrastructure.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventSpringDataRepository extends JpaRepository<OutboxEventJpaEntity, Long> {
    List<OutboxEventJpaEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            OutboxEvent.Status status,
            Instant nextAttemptAt,
            Pageable pageable
    );
}
