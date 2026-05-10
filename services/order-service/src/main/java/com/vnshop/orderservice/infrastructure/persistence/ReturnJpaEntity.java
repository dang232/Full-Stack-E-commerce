package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.Return;
import com.vnshop.orderservice.domain.ReturnStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(schema = "order_svc", name = "returns")
public class ReturnJpaEntity {
    @Id
    @Column(name = "return_id")
    private String returnId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "sub_order_id", nullable = false)
    private Long subOrderId;

    @Column(name = "buyer_id", nullable = false)
    private String buyerId;

    @Column(name = "reason", nullable = false, length = 2048)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReturnStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected ReturnJpaEntity() {
    }

    static ReturnJpaEntity fromDomain(Return orderReturn) {
        ReturnJpaEntity entity = new ReturnJpaEntity();
        entity.returnId = orderReturn.returnId();
        entity.orderId = orderReturn.orderId();
        entity.subOrderId = orderReturn.subOrderId();
        entity.buyerId = orderReturn.buyerId();
        entity.reason = orderReturn.reason();
        entity.status = orderReturn.status();
        entity.requestedAt = orderReturn.requestedAt();
        entity.resolvedAt = orderReturn.resolvedAt();
        return entity;
    }

    Return toDomain() {
        return new Return(returnId, orderId, subOrderId, buyerId, reason, status, requestedAt, resolvedAt);
    }
}
