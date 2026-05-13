package com.vnshop.orderservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SagaStateSpringDataRepository extends JpaRepository<SagaStateJpaEntity, String> {
    Optional<SagaStateJpaEntity> findByOrderId(String orderId);
}