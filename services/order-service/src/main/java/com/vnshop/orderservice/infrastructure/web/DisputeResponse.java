package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.Dispute;

public record DisputeResponse(
        String disputeId,
        String returnId,
        String buyerReason,
        String sellerResponse,
        String adminResolution,
        String status
) {

    static DisputeResponse fromDomain(Dispute dispute) {
        return new DisputeResponse(
                dispute.disputeId().toString(),
                dispute.returnId(),
                dispute.buyerReason(),
                dispute.sellerResponse(),
                dispute.adminResolution(),
                dispute.status().name()
        );
    }
}
