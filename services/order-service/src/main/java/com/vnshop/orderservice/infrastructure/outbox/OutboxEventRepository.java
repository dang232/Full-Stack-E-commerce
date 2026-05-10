package com.vnshop.orderservice.infrastructure.outbox;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEventJpaEntity, Long> {
    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAt(OutboxEvent.Status status, Pageable pageable);
}
