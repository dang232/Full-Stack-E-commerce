package com.vnshop.orderservice.domain;

import java.util.Objects;

public class Dispute {
    private final String disputeId;
    private final String returnId;
    private final String buyerReason;
    private String sellerResponse;
    private String adminResolution;
    private DisputeStatus status;

    public Dispute(String disputeId, String returnId, String buyerReason, String sellerResponse) {
        this(disputeId, returnId, buyerReason, sellerResponse, null, DisputeStatus.OPEN);
    }

    public Dispute(
            String disputeId,
            String returnId,
            String buyerReason,
            String sellerResponse,
            String adminResolution,
            DisputeStatus status
    ) {
        requireNonBlank(disputeId, "disputeId");
        requireNonBlank(returnId, "returnId");
        requireNonBlank(buyerReason, "buyerReason");
        this.disputeId = disputeId;
        this.returnId = returnId;
        this.buyerReason = buyerReason;
        this.sellerResponse = sellerResponse;
        this.adminResolution = adminResolution;
        this.status = Objects.requireNonNull(status, "status is required");
    }

    public String disputeId() {
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
