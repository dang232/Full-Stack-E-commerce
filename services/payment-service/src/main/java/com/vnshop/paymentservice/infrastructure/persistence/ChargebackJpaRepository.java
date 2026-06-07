package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.domain.Chargeback;
import com.vnshop.paymentservice.domain.port.out.ChargebackRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ChargebackJpaRepository implements ChargebackRepositoryPort {

    private final ChargebackSpringDataRepository springDataRepository;

    public ChargebackJpaRepository(ChargebackSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Chargeback save(Chargeback chargeback) {
        ChargebackJpaEntity entity = ChargebackJpaEntity.fromDomain(chargeback);
        return springDataRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Chargeback> findById(UUID id) {
        return springDataRepository.findByChargebackId(id).map(ChargebackJpaEntity::toDomain);
    }

    @Override
    public boolean existsByExternalChargebackId(String externalChargebackId) {
        return springDataRepository.existsByExternalChargebackId(externalChargebackId);
    }
}
