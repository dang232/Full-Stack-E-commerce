package com.vnshop.orderservice.application.saga;

import com.vnshop.orderservice.domain.port.out.OutboxPort;
import com.vnshop.orderservice.domain.port.out.SagaStateRepository;
import com.vnshop.orderservice.domain.saga.SagaState;
import com.vnshop.orderservice.domain.saga.SagaStatus;
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
    private final OutboxPort outboxPort;
    private final long compensationTimeoutMs;

    public SagaOrchestrator(
            SagaStateRepository sagaStateRepository,
            OutboxPort outboxPort,
            @Value("${saga.compensation-timeout-ms:300000}") long compensationTimeoutMs
    ) {
        this.sagaStateRepository = sagaStateRepository;
        this.outboxPort = outboxPort;
        this.compensationTimeoutMs = compensationTimeoutMs;
    }

    @Transactional
    public SagaState startOrderSaga(String orderId) {
        String sagaId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        SagaState sagaState = new SagaState(sagaId, orderId, SagaStatus.STARTED, now, now);
        SagaState saved = sagaStateRepository.save(sagaState);

        outboxPort.publish(AGGREGATE_TYPE, sagaId, "SAGA_STARTED",
            "{\"orderId\":\"" + orderId + "\",\"sagaId\":\"" + sagaId + "\",\"step\":\"INVENTORY_RESERVE\"}");

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

        outboxPort.publish(AGGREGATE_TYPE, sagaId, "SAGA_STEP_COMPLETED",
            "{\"orderId\":\"" + current.orderId() + "\",\"sagaId\":\"" + sagaId + "\",\"step\":\"PAYMENT_CHARGE\"}");

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

        outboxPort.publish(AGGREGATE_TYPE, sagaId, "SAGA_STEP_COMPLETED",
            "{\"orderId\":\"" + current.orderId() + "\",\"sagaId\":\"" + sagaId + "\",\"step\":\"SHIPPING_CREATE\"}");

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

        outboxPort.publish(AGGREGATE_TYPE, sagaId, "SAGA_COMPLETED",
            "{\"orderId\":\"" + current.orderId() + "\",\"sagaId\":\"" + sagaId + "\",\"step\":\"COMPLETE\"}");

        // Persist terminal COMPLETED state
        SagaState completed = new SagaState(current.sagaId(), current.orderId(), SagaStatus.COMPLETED, current.createdAt(), Instant.now());
        sagaStateRepository.save(completed);

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
        SagaState compensating = new SagaState(current.sagaId(), current.orderId(),
            SagaStatus.COMPENSATING, current.createdAt(), Instant.now());
        sagaStateRepository.save(compensating);

        String orderId = current.orderId();
        outboxPort.publish("Order", orderId, "SAGA_COMPENSATING",
            "{\"sagaId\":\"" + sagaId + "\",\"orderId\":\"" + orderId + "\",\"failedStep\":\"" + failedStep + "\"}");

        switch (failedStep) {
            case "SHIPPING":
                // Payment was charged, inventory was reserved — reverse both
                outboxPort.publish("Order", orderId, "PAYMENT_REFUND_REQUESTED",
                    "{\"orderId\":\"" + orderId + "\",\"sagaId\":\"" + sagaId + "\"}");
                outboxPort.publish("Order", orderId, "INVENTORY_RELEASE_REQUESTED",
                    "{\"orderId\":\"" + orderId + "\",\"sagaId\":\"" + sagaId + "\"}");
                break;
            case "PAYMENT":
            case "PAYMENT_CHARGE":
                // Only inventory was reserved — release it
                outboxPort.publish("Order", orderId, "INVENTORY_RELEASE_REQUESTED",
                    "{\"orderId\":\"" + orderId + "\",\"sagaId\":\"" + sagaId + "\"}");
                break;
            case "INVENTORY":
                // Nothing to compensate — first step failed
                markFailed(sagaId);
                break;
            default:
                LOG.error("Unknown saga step: {}", failedStep);
                markFailed(sagaId);
        }
        LOG.warn("Saga {} compensation initiated at step: {}", sagaId, failedStep);
    }

    @Transactional
    public void onCompensationStepCompleted(String sagaId, String step) {
        LOG.info("Saga {} compensation step completed: {}", sagaId, step);
        // Any compensation step completing marks the saga as FAILED
        // (meaning compensation is done, the order creation failed)
        markFailed(sagaId);
    }

    public void start(String sagaId, String orderId) {
        sagaStateRepository.save(new SagaState(sagaId, orderId, SagaStatus.STARTED, Instant.now(), null));
    }

    public void stepCompleted(String sagaId, String step) {
        LOG.debug("Saga {} step completed: {}", sagaId, step);
    }

    @Transactional
    public void complete(String sagaId) {
        sagaStateRepository.findBySagaId(sagaId).ifPresent(s -> {
            sagaStateRepository.save(new SagaState(s.sagaId(), s.orderId(),
                SagaStatus.COMPLETED, s.createdAt(), Instant.now()));
        });
    }

    public Optional<String> getLastCompletedStep(String sagaId) {
        return Optional.empty(); // Simplified — full impl would track step list
    }

    private void markFailed(String sagaId) {
        sagaStateRepository.findBySagaId(sagaId).ifPresent(s -> {
            SagaState failed = new SagaState(s.sagaId(), s.orderId(),
                SagaStatus.FAILED, s.createdAt(), Instant.now());
            sagaStateRepository.save(failed);
        });
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

        outboxPort.publish(AGGREGATE_TYPE, current.sagaId(), "SAGA_FAILED",
            "{\"orderId\":\"" + current.orderId() + "\",\"sagaId\":\"" + current.sagaId() + "\",\"reason\":\"" + reason + "\"}");

        LOG.error("Saga {} marked FAILED after compensation timeout", current.sagaId());
    }
}
