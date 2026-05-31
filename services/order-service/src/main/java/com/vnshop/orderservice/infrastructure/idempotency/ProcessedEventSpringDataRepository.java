package com.vnshop.orderservice.infrastructure.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventSpringDataRepository extends JpaRepository<ProcessedEvent, String> {
}
