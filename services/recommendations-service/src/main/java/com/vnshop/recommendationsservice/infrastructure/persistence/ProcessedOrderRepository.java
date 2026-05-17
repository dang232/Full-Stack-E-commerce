package com.vnshop.recommendationsservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrderJpaEntity, String> {
}
