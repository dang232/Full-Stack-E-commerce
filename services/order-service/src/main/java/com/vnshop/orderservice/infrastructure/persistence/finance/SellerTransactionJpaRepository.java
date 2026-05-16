package com.vnshop.orderservice.infrastructure.persistence.finance;

import com.vnshop.orderservice.domain.finance.SellerTransaction;
import com.vnshop.orderservice.domain.finance.port.out.SellerTransactionRepositoryPort;
import org.springframework.stereotype.Repository;

@Repository
public class SellerTransactionJpaRepository implements SellerTransactionRepositoryPort {
    private final SellerTransactionSpringDataRepository repository;

    public SellerTransactionJpaRepository(SellerTransactionSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return repository.existsByIdempotencyKey(idempotencyKey);
    }

    @Override
    public SellerTransaction save(SellerTransaction transaction) {
        return repository.save(SellerTransactionJpaEntity.fromDomain(transaction)).toDomain();
    }
}
