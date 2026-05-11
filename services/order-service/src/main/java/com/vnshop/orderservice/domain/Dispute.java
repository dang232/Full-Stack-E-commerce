package com.vnshop.orderservice.domain;

import java.util.Objects;
import java.util.UUID;

public class Dispute {
    private final UUID disputeId;
    private final String returnId;
    private final String buyerReason;
    private String sellerResponse;
    private String adminResolution;
    private DisputeStatus status;

    public Dispute(UUID disputeId, String returnId, String buyerReason, String sellerResponse) {
        this(disputeId, returnId, buyerReason, sellerResponse, null, DisputeStatus.OPEN);
    }

    public Dispute(
            UUID disputeId,
            String returnId,
            String buyerReason,
            String sellerResponse,
            String adminResolution,
            DisputeStatus status
    ) {
        Objects.requireNonNull(disputeId, "disputeId is required");
        requireNonBlank(returnId, "returnId");
        requireNonBlank(buyerReason, "buyerReason");
        this.disputeId = disputeId;
        this.returnId = returnId;
        this.buyerReason = buyerReason;
        this.sellerResponse = sellerResponse;
        this.adminResolution = adminResolution;
        this.status = Objects.requireNonNull(status, "status is required");
    }

    public UUID disputeId() {
        return disputeId;
    }

    public String returnId() {
        return returnId;
    }

    public String buyerReason() {
        return buyerReason;
    }

    public String sellerResponse() {
        return sellerResponse;
    }

    public String adminResolution() {
        return adminResolution;
    }

    public DisputeStatus status() {
        return status;
    }

    public void resolve(String adminResolution) {
        requireNonBlank(adminResolution, "adminResolution");
        if (status != DisputeStatus.OPEN) {
            throw new IllegalStateException("cannot resolve dispute from " + status);
        }
        this.adminResolution = adminResolution;
        this.status = DisputeStatus.RESOLVED;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
