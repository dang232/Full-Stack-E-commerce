package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.Invoice;

import java.time.Instant;

public record InvoiceResponse(
        String id,
        String orderId,
        Long subOrderId,
        String checksumSha256,
        int version,
        Instant generatedAt
) {

    static InvoiceResponse fromDomain(Invoice invoice) {
        return new InvoiceResponse(
                invoice.id().toString(),
                invoice.orderId().toString(),
                invoice.subOrderId(),
                invoice.checksumSha256(),
                invoice.version(),
                invoice.generatedAt()
        );
    }
}
