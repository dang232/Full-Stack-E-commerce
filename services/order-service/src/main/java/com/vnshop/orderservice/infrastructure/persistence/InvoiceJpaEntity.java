package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.Invoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(schema = "order_svc", name = "invoices")
public class InvoiceJpaEntity {
    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "sub_order_id", nullable = false, unique = true)
    private Long subOrderId;

    @Column(name = "buyer_id", nullable = false)
    private String buyerId;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    @Column(name = "object_key", nullable = false, unique = true, length = 1024)
    private String objectKey;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected InvoiceJpaEntity() {
    }

    static InvoiceJpaEntity fromDomain(Invoice invoice) {
        InvoiceJpaEntity entity = new InvoiceJpaEntity();
        entity.id = invoice.id();
        entity.orderId = invoice.orderId();
        entity.subOrderId = invoice.subOrderId();
        entity.buyerId = invoice.buyerId();
        entity.sellerId = invoice.sellerId();
        entity.objectKey = invoice.objectKey();
        entity.checksumSha256 = invoice.checksumSha256();
        entity.version = invoice.version();
        entity.generatedAt = invoice.generatedAt();
        return entity;
    }

    Invoice toDomain() {
        return new Invoice(id, orderId, subOrderId, buyerId, sellerId, objectKey, checksumSha256, version, generatedAt);
    }
}
