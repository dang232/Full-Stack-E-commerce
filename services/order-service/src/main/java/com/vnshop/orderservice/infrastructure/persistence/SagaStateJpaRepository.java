package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.port.out.SagaStateRepository;
import com.vnshop.orderservice.domain.saga.SagaState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SagaStateJpaRepository implements SagaStateRepository {
    private final SagaStateSpringDataRepository springDataRepository;

    public SagaStateJpaRepository(SagaStateSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public SagaState save(SagaState sagaState) {
        SagaStateJpaEntity entity = springDataRepository.findById(sagaState.sagaId())
                .map(existing -> {
                    existing.applyDomain(sagaState);
                    return existing;
                })
                .orElseGet(() -> SagaStateJpaEntity.fromDomain(sagaState));
        return springDataRepository.save(entity).toDomain();
    }

    @Override
    public Optional<SagaState> findBySagaId(String sagaId) {
        return springDataRepository.findById(sagaId).map(SagaStateJpaEntity::toDomain);
    }

    @Override
    public Optional<SagaState> findByOrderId(String orderId) {
        return springDataRepository.findByOrderId(orderId).map(SagaStateJpaEntity::toDomain);
    }

    @Override
    public List<SagaState> findCompensatingUpdatedBefore(Instant cutoff) {
        return springDataRepository
                .findByCurrentStepAndUpdatedAtBefore(com.vnshop.orderservice.domain.saga.SagaStatus.COMPENSATING, cutoff)
                .stream()
                .map(SagaStateJpaEntity::toDomain)
                .toList();
    }
}