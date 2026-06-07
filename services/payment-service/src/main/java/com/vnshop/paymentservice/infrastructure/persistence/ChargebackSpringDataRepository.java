package com.vnshop.paymentservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChargebackSpringDataRepository extends JpaRepository<ChargebackJpaEntity, Long> {
    Optional<ChargebackJpaEntity> findByChargebackId(UUID chargebackId);
    boolean existsByExternalChargebackId(String externalChargebackId);
}
