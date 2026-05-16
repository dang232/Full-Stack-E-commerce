package com.vnshop.orderservice.infrastructure.persistence.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SellerTransactionSpringDataRepository
        extends JpaRepository<SellerTransactionJpaEntity, UUID> {
    boolean existsByIdempotencyKey(String idempotencyKey);
}
