package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Dispute;
import com.vnshop.orderservice.domain.DisputeStatus;
import com.vnshop.orderservice.domain.port.out.DisputeRepositoryPort;

import java.util.List;
import java.util.Objects;

public class DisputeQueryUseCase {
    private final DisputeRepositoryPort disputeRepository;

    public DisputeQueryUseCase(DisputeRepositoryPort disputeRepository) {
        this.disputeRepository = Objects.requireNonNull(disputeRepository, "disputeRepository is required");
    }

    public List<Dispute> findOpen() {
        return disputeRepository.findByStatus(DisputeStatus.OPEN.name());
    }
}
