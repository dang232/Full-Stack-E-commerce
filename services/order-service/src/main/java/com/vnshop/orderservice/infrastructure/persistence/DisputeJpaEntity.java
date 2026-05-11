package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.Dispute;
import com.vnshop.orderservice.domain.DisputeStatus;
import com.vnshop.orderservice.infrastructure.persistence.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "order_svc", name = "disputes")
@Getter
@Setter
public class DisputeJpaEntity extends BaseJpaEntity {
    @Id
    @Column(name = "dispute_id", columnDefinition = "uuid")
    private UUID disputeId;

    @Column(name = "return_id", nullable = false, columnDefinition = "uuid")
    private UUID returnId;

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
        entity.returnId = UUID.fromString(dispute.returnId());
        entity.buyerReason = dispute.buyerReason();
        entity.sellerResponse = dispute.sellerResponse();
        entity.adminResolution = dispute.adminResolution();
        entity.status = dispute.status();
        return entity;
    }

    Dispute toDomain() {
        return new Dispute(disputeId, returnId.toString(), buyerReason, sellerResponse, adminResolution, status);
    }
}
