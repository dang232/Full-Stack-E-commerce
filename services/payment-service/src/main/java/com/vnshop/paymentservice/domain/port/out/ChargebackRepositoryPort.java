package com.vnshop.paymentservice.domain.port.out;

import com.vnshop.paymentservice.domain.Chargeback;

import java.util.Optional;
import java.util.UUID;

public interface ChargebackRepositoryPort {
    Chargeback save(Chargeback chargeback);
    Optional<Chargeback> findById(UUID id);
    boolean existsByExternalChargebackId(String externalChargebackId);
}
