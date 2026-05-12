package com.vnshop.orderservice.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReturnJpaSpringDataRepository extends JpaRepository<ReturnJpaEntity, UUID> {
    List<ReturnJpaEntity> findByBuyerId(String buyerId);
}
