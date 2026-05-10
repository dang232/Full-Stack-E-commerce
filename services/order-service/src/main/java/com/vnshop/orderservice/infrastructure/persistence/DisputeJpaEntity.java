package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.Dispute;
import com.vnshop.orderservice.domain.DisputeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "order_svc", name = "disputes")
public class DisputeJpaEntity {
    @Id
    @Column(name = "dispute_id")
    private String disputeId;

    @Column(name = "return_id", nullable = false)
    private String returnId;

    @Column(name = "buyer_reason", nullable = false, length = 2048)
    private String buyerReason;

    @Column(name = "seller_response", length = 2048)
    private String sellerResponse;

    @Column(name = "admin_resolution", length = 2048)
    private String adminResolution;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DisputeStatus status;

    protected DisputeJpaEntity() {
    }

    static DisputeJpaEntity fromDomain(Dispute dispute) {
        DisputeJpaEntity entity = new DisputeJpaEntity();
        entity.disputeId = dispute.disputeId();
        entity.returnId = dispute.returnId();
        entity.buyerReason = dispute.buyerReason();
        entity.sellerResponse = dispute.sellerResponse();
        entity.adminResolution = dispute.adminResolution();
        entity.status = dispute.status();
        return entity;
    }

    Dispute toDomain() {
        return new Dispute(disputeId, returnId, buyerReason, sellerResponse, adminResolution, status);
    }
}
