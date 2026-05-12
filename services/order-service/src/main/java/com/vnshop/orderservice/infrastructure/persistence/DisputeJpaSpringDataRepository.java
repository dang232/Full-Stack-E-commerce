package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.DisputeStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DisputeJpaSpringDataRepository extends JpaRepository<DisputeJpaEntity, UUID> {
    List<DisputeJpaEntity> findByStatus(DisputeStatus status);
}
