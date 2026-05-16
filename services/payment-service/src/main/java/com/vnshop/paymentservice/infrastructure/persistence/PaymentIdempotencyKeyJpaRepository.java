package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.domain.PaymentIdempotencyKey;
import com.vnshop.paymentservice.domain.port.out.PaymentIdempotencyKeyRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PaymentIdempotencyKeyJpaRepository implements PaymentIdempotencyKeyRepositoryPort {
    private final PaymentIdempotencyKeySpringDataRepository springDataRepository;

    public PaymentIdempotencyKeyJpaRepository(PaymentIdempotencyKeySpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<PaymentIdempotencyKey> findByKey(String key) {
        return springDataRepository.findById(key).map(PaymentIdempotencyKeyJpaEntity::toDomain);
    }

    @Override
    public PaymentIdempotencyKey save(PaymentIdempotencyKey key) {
        return springDataRepository.save(PaymentIdempotencyKeyJpaEntity.fromDomain(key)).toDomain();
    }
}
