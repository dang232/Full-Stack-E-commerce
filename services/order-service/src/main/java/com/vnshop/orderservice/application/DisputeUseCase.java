package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Dispute;
import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.port.out.DisputeRepositoryPort;
import com.vnshop.orderservice.domain.port.out.ReturnRepositoryPort;

import java.util.Objects;
import java.util.UUID;

public class DisputeUseCase {
    private final ReturnRepositoryPort returnRepository;
    private final DisputeRepositoryPort disputeRepository;

    public DisputeUseCase(ReturnRepositoryPort returnRepository, DisputeRepositoryPort disputeRepository) {
        this.returnRepository = Objects.requireNonNull(returnRepository, "returnRepository is required");
        this.disputeRepository = Objects.requireNonNull(disputeRepository, "disputeRepository is required");
    }

    public Dispute open(String returnId, String buyerReason, String sellerResponse) {
        Return orderReturn = returnRepository.findById(returnId)
                .orElseThrow(() -> new IllegalArgumentException("return not found: " + returnId));
        return disputeRepository.save(new Dispute(UUID.randomUUID().toString(), orderReturn.returnId(), buyerReason, sellerResponse));
    }

    public Dispute resolve(String disputeId, String adminResolution) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("dispute not found: " + disputeId));
        dispute.resolve(adminResolution);
        return disputeRepository.save(dispute);
    }
}
