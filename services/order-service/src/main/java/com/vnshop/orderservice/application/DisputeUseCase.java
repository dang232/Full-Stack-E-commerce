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

    /**
     * Pt14 audit fix: only the buyer who opened the return may escalate it
     * into a dispute. Without this check any authenticated buyer could open
     * a dispute on any other buyer's return by guessing the returnId UUID,
     * which would surface in the admin disputes queue and waste admin time
     * with bogus rows.
     */
    public Dispute open(UUID returnId, String buyerId, String buyerReason, String sellerResponse) {
        if (buyerId == null || buyerId.isBlank()) {
            throw new IllegalArgumentException("buyerId is required");
        }
        Return orderReturn = returnRepository.findById(returnId)
                .orElseThrow(() -> new IllegalArgumentException("return not found: " + returnId));
        if (!buyerId.equals(orderReturn.buyerId())) {
            throw new OrderAccessDeniedException(
                    "buyer " + buyerId + " does not own return " + returnId);
        }
        return disputeRepository.save(new Dispute(UUID.randomUUID(), orderReturn.returnId().toString(), buyerReason, sellerResponse));
    }

    public Dispute resolve(UUID disputeId, String adminResolution, String resolvedBy) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("dispute not found: " + disputeId));
        dispute.resolve(adminResolution, resolvedBy);
        return disputeRepository.save(dispute);
    }
}
