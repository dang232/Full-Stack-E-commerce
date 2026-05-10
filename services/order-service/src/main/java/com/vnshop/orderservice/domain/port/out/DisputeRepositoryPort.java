package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.Dispute;

import java.util.List;
import java.util.Optional;

public interface DisputeRepositoryPort {
    Dispute save(Dispute dispute);

    Optional<Dispute> findById(String disputeId);

    List<Dispute> findByStatus(String status);
}
