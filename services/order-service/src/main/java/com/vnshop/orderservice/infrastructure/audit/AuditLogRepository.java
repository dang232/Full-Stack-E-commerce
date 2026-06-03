package com.vnshop.orderservice.infrastructure.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogJpaEntity, Long> {
}
