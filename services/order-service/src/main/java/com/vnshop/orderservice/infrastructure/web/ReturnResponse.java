package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.Return;

import java.time.Instant;

public record ReturnResponse(
        String returnId,
        String orderId,
        Long subOrderId,
        String buyerId,
        String reason,
        String status,
        Instant requestedAt,
        Instant resolvedAt
) {

    static ReturnResponse fromDomain(Return orderReturn) {
        return new ReturnResponse(
                orderReturn.returnId().toString(),
                orderReturn.orderId(),
                orderReturn.subOrderId(),
                orderReturn.buyerId(),
                orderReturn.reason(),
                orderReturn.status().name(),
                orderReturn.requestedAt(),
                orderReturn.resolvedAt()
        );
    }
}
