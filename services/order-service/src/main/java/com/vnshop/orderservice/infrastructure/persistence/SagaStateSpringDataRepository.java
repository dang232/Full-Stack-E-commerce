package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.saga.SagaStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaStateSpringDataRepository extends JpaRepository<SagaStateJpaEntity, String> {
    Optional<SagaStateJpaEntity> findByOrderId(String orderId);
    List<SagaStateJpaEntity> findByCurrentStepAndUpdatedAtBefore(SagaStatus currentStep, Instant updatedAt);
}
