package com.vnshop.orderservice.application.saga;

import com.vnshop.orderservice.domain.port.out.SagaStateRepository;
import com.vnshop.orderservice.domain.saga.SagaState;
import com.vnshop.orderservice.domain.saga.SagaStatus;
import com.vnshop.orderservice.infrastructure.outbox.OutboxEvent;
import com.vnshop.orderservice.infrastructure.outbox.OutboxEventJpaEntity;
import com.vnshop.orderservice.infrastructure.outbox.OutboxEventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SagaOrchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(SagaOrchestrator.class);
    private static final String AGGREGATE_TYPE = "SAGA";

    private final SagaStateRepository sagaStateRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final long compensationTimeoutMs;

    public SagaOrchestrator(
            SagaStateRepository sagaStateRepository,
            OutboxEventRepository outboxEventRepository,
            @Value("${saga.compensation-timeout-ms:300000}") long compensationTimeoutMs
    ) {
        this.sagaStateRepository = sagaStateRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.compensationTimeoutMs = compensationTimeoutMs;
    }

    @Transactional
    public SagaState startOrderSaga(String orderId) {
        String sagaId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        SagaState sagaState = new SagaState(sagaId, orderId, SagaStatus.STARTED, now, now);
        SagaState saved = sagaStateRepository.save(sagaState);

        outboxEventRepository.save(OutboxEventJpaEntity.fromDomain(
            OutboxEvent.pending(AGGREGATE_TYPE, sagaId, "SAGA_STARTED",
                "{\"orderId\":\"" + orderId + "\",\"sagaId\":\"" + sagaId + "\",\"step\":\"INVENTORY_RESERVE\"}")
        ));

        LOG.info("Saga {} started for order {}", sagaId, orderId);
        return saved;
    }

    @Transactional
    public void onInventoryReserved(String sagaId) {
        Optional<SagaState> opt = sagaStateRepository.findBySagaId(sagaId);
        if (opt.isEmpty()) {
            LOG.warn("Saga {} not found for inventory reserved", sagaId);
            return;
        }
        SagaState current = opt.get();
        SagaState updated = new SagaState(current.sagaId(), current.orderId(), SagaStatus.INVENTORY_RESERVED, current.createdAt(), Instant.now());
        sagaStateRepository.save(updated);

        outboxEventRepository.save(OutboxEventJpaEntity.fromDomain(
            OutboxEvent.pending(AGGREGATE_TYPE, sagaId, "SAGA_STEP_COMPLETED",
                "{\"orderId\":\"" + current.orderId() + "\",\"sagaId\":\"" + sagaId + "\",\"step\":\"PAYMENT_CHARGE\"}")
        ));

        LOG.info("Saga {} step: INVENTORY_RESERVED -> PAYMENT_CHARGED", sagaId);
    }

    @Transactional
    public void onPaymentCharged(String sagaId) {
        Optional<SagaState> opt = sagaStateRepository.findBySagaId(sagaId);
        if (opt.isEmpty()) {
            LOG.warn("Saga {} not found for payment charged", sagaId);
            return;
        }
        SagaState current = opt.get();
        SagaState updated = new SagaState(current.sagaId(), current.orderId(), SagaStatus.PAYMENT_CHARGED, current.createdAt(), Instant.now());
        sagaStateRepository.save(updated);

        outboxEventRepository.save(OutboxEventJpaEntity.fromDomain(
            OutboxEvent.pending(AGGREGATE_TYPE, sagaId, "SAGA_STEP_COMPLETED",
                "{\"orderId\":\"" + current.orderId() + "\",\"sagaId\":\"" + sagaId + "\",\"step\":\"SHIPPING_CREATE\"}")
        ));

        LOG.info("Saga {} step: PAYMENT_CHARGED -> SHIPPING_CREATED", sagaId);
    }

    @Transactional
    public void onShippingCreated(String sagaId) {
        Optional<SagaState> opt = sagaStateRepository.findBySagaId(sagaId);
        if (opt.isEmpty()) {
            LOG.warn("Saga {} not found for shipping created", sagaId);
            return;
        }
        SagaState current = opt.get();
        SagaState updated = new SagaState(current.sagaId(), current.orderId(), SagaStatus.SHIPPING_CREATED, current.createdAt(), Instant.now());
        sagaStateRepository.save(updated);

        outboxEventRepository.save(OutboxEventJpaEntity.fromDomain(
            OutboxEvent.pending(AGGREGATE_TYPE, sagaId, "SAGA_COMPLETED",
                "{\"orderId\":\"" + current.orderId() + "\",\"sagaId\":\"" + sagaId + "\",\"step\":\"COMPLETE\"}")
        ));

        LOG.info("Saga {} completed for order {}", sagaId, current.orderId());
    }

    @Transactional
    public void compensate(String sagaId, String failedStep) {
        Optional<SagaState> opt = sagaStateRepository.findBySagaId(sagaId);
        if (opt.isEmpty()) {
            LOG.warn("Saga {} not found for compensation", sagaId);
            return;
        }
        SagaState current = opt.get();
        SagaState compensating = new SagaState(current.sagaId(), current.orderId(), SagaStatus.COMPENSATING, current.createdAt(), Instant.now());
        sagaStateRepository.save(compensating);

        outboxEventRepository.save(OutboxEventJpaEntity.fromDomain(
            OutboxEvent.pending(AGGREGATE_TYPE, sagaId, "SAGA_COMPENSATING",
                "{\"orderId\":\"" + current.orderId() + "\",\"sagaId\":\"" + sagaId + "\",\"failedStep\":\"" + failedStep + "\"}")
        ));

        LOG.warn("Saga {} compensation requested at step: {}", sagaId, failedStep);
    }

    /**
     * Called when a downstream service confirms its compensation work has completed
     * (e.g. inventory release, payment refund, shipping cancellation). Promotes a
     * COMPENSATING saga directly to FAILED so listeners and dashboards see a real
     * terminal state without waiting for the timeout finalizer.
     *
     * <p>Wire downstream confirmation Kafka events here (e.g. {@code inventory.released},
     * {@code payment.refunded}, {@code shipping.cancelled}). Until those exist, the
     * timeout finalizer remains the only fallback.
     */
    @Transactional
    public void onCompensationCompleted(String sagaId, String confirmingStep) {
        Optional<SagaState> opt = sagaStateRepository.findBySagaId(sagaId);
        if (opt.isEmpty()) {
            LOG.warn("Saga {} not found for compensation confirmation from {}", sagaId, confirmingStep);
            return;
        }
        markCompensationFailed(opt.get(), "COMPENSATION_CONFIRMED:" + confirmingStep);
    }

    @Scheduled(fixedDelayString = "${saga.compensation-finalizer-interval-ms:60000}")
    @Transactional
    public void failTimedOutCompensations() {
        Instant cutoff = Instant.now().minusMillis(compensationTimeoutMs);
        for (SagaState saga : sagaStateRepository.findCompensatingUpdatedBefore(cutoff)) {
            markCompensationFailed(saga, "COMPENSATION_TIMEOUT");
        }
    }

    void markCompensationFailed(SagaState current, String reason) {
        if (current.currentStep() != SagaStatus.COMPENSATING) {
            return;
        }

        SagaState failed = new SagaState(current.sagaId(), current.orderId(), SagaStatus.FAILED, current.createdAt(), Instant.now());
        sagaStateRepository.save(failed);

        outboxEventRepository.save(OutboxEventJpaEntity.fromDomain(
                OutboxEvent.pending(AGGREGATE_TYPE, current.sagaId(), "SAGA_FAILED",
                        "{\"orderId\":\"" + current.orderId() + "\",\"sagaId\":\"" + current.sagaId() + "\",\"reason\":\"" + reason + "\"}")
        ));

        LOG.error("Saga {} marked FAILED after compensation timeout", current.sagaId());
    }
}
