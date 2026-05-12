package com.vnshop.orderservice.infrastructure.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessedEventSpringDataRepository extends JpaRepository<ProcessedEvent, String> {
}
