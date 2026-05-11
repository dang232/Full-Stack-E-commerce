package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.infrastructure.persistence.BaseJpaEntity;
import com.vnshop.paymentservice.infrastructure.gateway.PaymentCallbackOutboxRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(schema = "payment_svc", name = "payment_callback_outbox")
public class PaymentCallbackOutboxJpaEntity extends BaseJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "payment_id", nullable = false, columnDefinition = "uuid")
    private UUID paymentId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "transaction_ref", nullable = false)
    private String transactionRef;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "callback_id", nullable = false, columnDefinition = "uuid")
    private UUID callbackId;

    @Column(name = "callback_event_id")
    private String callbackEventId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;


    @Column(name = "published_at")
    private Instant publishedAt;

    protected PaymentCallbackOutboxJpaEntity() {
    }

    static PaymentCallbackOutboxJpaEntity fromRecord(PaymentCallbackOutboxRecord record) {
        PaymentCallbackOutboxJpaEntity entity = new PaymentCallbackOutboxJpaEntity();
        entity.id = record.id();
        entity.provider = record.provider();
        entity.paymentId = record.paymentId();
        entity.orderId = record.orderId();
        entity.transactionRef = record.transactionRef();
        entity.status = record.status();
        entity.amount = record.amount();
        entity.currency = record.currency();
        entity.callbackId = record.callbackId();
        entity.callbackEventId = record.callbackEventId();
        entity.payloadHash = record.payloadHash();
        entity.publishedAt = record.publishedAt();
        return entity;
    }

    PaymentCallbackOutboxRecord toRecord() {
        return new PaymentCallbackOutboxRecord(id, provider, paymentId, orderId, transactionRef, status, amount, currency, callbackId, callbackEventId, payloadHash, getCreatedAt(), publishedAt);
    }
}
