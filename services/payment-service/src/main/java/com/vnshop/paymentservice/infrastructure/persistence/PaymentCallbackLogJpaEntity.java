package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.infrastructure.persistence.BaseJpaEntity;
import com.vnshop.paymentservice.infrastructure.gateway.PaymentCallbackAttempt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(schema = "payment_svc", name = "payment_callback_logs")
public class PaymentCallbackLogJpaEntity extends BaseJpaEntity {
    @Id
    @Column(name = "callback_id", nullable = false, columnDefinition = "uuid")
    private UUID callbackId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "signature_hash", nullable = false, length = 64)
    private String signatureHash;

    @Column(name = "request_headers", nullable = false, columnDefinition = "TEXT")
    private String requestHeaders;

    @Column(name = "request_body", nullable = false, columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processing_status", nullable = false, length = 32)
    private String processingStatus;

    @Column(name = "duplicate_replay", nullable = false)
    private boolean duplicateReplay;

    protected PaymentCallbackLogJpaEntity() {
    }

    static PaymentCallbackLogJpaEntity fromAttempt(PaymentCallbackAttempt attempt) {
        PaymentCallbackLogJpaEntity entity = new PaymentCallbackLogJpaEntity();
        entity.callbackId = attempt.callbackId();
        entity.provider = attempt.provider();
        entity.eventId = attempt.eventId();
        entity.payloadHash = attempt.payloadHash();
        entity.signatureHash = attempt.signatureHash();
        entity.requestHeaders = attempt.headersJson();
        entity.requestBody = attempt.bodyJson();
        entity.receivedAt = attempt.receivedAt();
        entity.processingStatus = attempt.processingStatus();
        entity.duplicateReplay = attempt.duplicateReplay();
        return entity;
    }

    PaymentCallbackAttempt toAttempt() {
        return new PaymentCallbackAttempt(callbackId, provider, eventId, payloadHash, signatureHash, requestHeaders, requestBody, receivedAt, processingStatus, duplicateReplay);
    }
}
