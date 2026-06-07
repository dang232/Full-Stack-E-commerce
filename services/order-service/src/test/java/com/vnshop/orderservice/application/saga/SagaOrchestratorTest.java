package com.vnshop.orderservice.application.saga;

import com.vnshop.orderservice.domain.port.out.OutboxPort;
import com.vnshop.orderservice.domain.port.out.SagaStateRepository;
import com.vnshop.orderservice.domain.saga.SagaState;
import com.vnshop.orderservice.domain.saga.SagaStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SagaOrchestratorTest {
    private SagaOrchestrator orchestrator;
    private InMemorySagaStateRepository sagaStateRepo;
    private InMemoryOutboxPort outboxPort;

    @BeforeEach
    void setUp() {
        sagaStateRepo = new InMemorySagaStateRepository();
        outboxPort = new InMemoryOutboxPort();
        orchestrator = new SagaOrchestrator(sagaStateRepo, outboxPort, 1_000);
    }

    @Test
    void startOrderSaga_createsSagaState_withSTARTEDstatus() {
        SagaState saga = orchestrator.startOrderSaga("order-1");

        assertThat(saga.orderId()).isEqualTo("order-1");
        assertThat(saga.currentStep()).isEqualTo(SagaStatus.STARTED);
        assertThat(saga.sagaId()).isNotBlank();
        assertThat(saga.createdAt()).isNotNull();
        assertThat(sagaStateRepo.stored).isNotNull();
        assertThat(outboxPort.lastEvent().eventType()).isEqualTo("SAGA_STARTED");
    }

    @Test
    void happyPath_allStepsComplete_sagaReachesCOMPLETED() {
        SagaState started = orchestrator.startOrderSaga("order-1");
        String sagaId = started.sagaId();

        orchestrator.onInventoryReserved(sagaId);
        SagaState afterInventory = sagaStateRepo.findBySagaId(sagaId).orElseThrow();
        assertThat(afterInventory.currentStep()).isEqualTo(SagaStatus.INVENTORY_RESERVED);

        orchestrator.onPaymentCharged(sagaId);
        SagaState afterPayment = sagaStateRepo.findBySagaId(sagaId).orElseThrow();
        assertThat(afterPayment.currentStep()).isEqualTo(SagaStatus.PAYMENT_CHARGED);

        orchestrator.onShippingCreated(sagaId);
        SagaState afterShipping = sagaStateRepo.findBySagaId(sagaId).orElseThrow();
        assertThat(afterShipping.currentStep()).isEqualTo(SagaStatus.COMPLETED);

        // 4 outbox events: SAGA_STARTED, SAGA_STEP_COMPLETED x2, SAGA_COMPLETED
        assertThat(outboxPort.events).hasSize(4);
    }

    @Test
    void onShippingCreated_persistsCOMPLETEDstateAfterOutboxEvent() {
        SagaState started = orchestrator.startOrderSaga("order-1");
        String sagaId = started.sagaId();

        orchestrator.onShippingCreated(sagaId);

        SagaState finalState = sagaStateRepo.findBySagaId(sagaId).orElseThrow();
        assertThat(finalState.currentStep()).isEqualTo(SagaStatus.COMPLETED);

        InMemoryOutboxPort.PublishedEvent completedEvent = outboxPort.events.stream()
                .filter(e -> "SAGA_COMPLETED".equals(e.eventType()))
                .findFirst()
                .orElseThrow();
        assertThat(completedEvent.payload()).contains("\"step\":\"COMPLETE\"");
    }

    @Test
    void compensate_transitionsTo_COMPENSATING_andEmitsCompensatingEvent() {
        SagaState started = orchestrator.startOrderSaga("order-1");
        String sagaId = started.sagaId();

        orchestrator.compensate(sagaId, "PAYMENT_CHARGE");

        SagaState result = sagaStateRepo.findBySagaId(sagaId).orElseThrow();
        assertThat(result.currentStep()).isEqualTo(SagaStatus.COMPENSATING);
        InMemoryOutboxPort.PublishedEvent compensatingEvent = outboxPort.events.stream()
                .filter(e -> "SAGA_COMPENSATING".equals(e.eventType()))
                .findFirst()
                .orElseThrow();
        assertThat(compensatingEvent.payload()).contains("\"failedStep\":\"PAYMENT_CHARGE\"");
    }

    @Test
    void failTimedOutCompensations_marksOldCompensatingSagaFailedAndEmitsEvent() {
        Instant old = Instant.now().minusSeconds(10);
        SagaState compensating = new SagaState("saga-1", "order-1", SagaStatus.COMPENSATING, old, old);
        sagaStateRepo.save(compensating);

        orchestrator.failTimedOutCompensations();

        SagaState result = sagaStateRepo.findBySagaId("saga-1").orElseThrow();
        assertThat(result.currentStep()).isEqualTo(SagaStatus.FAILED);
        InMemoryOutboxPort.PublishedEvent last = outboxPort.events.get(outboxPort.events.size() - 1);
        assertThat(last.eventType()).isEqualTo("SAGA_FAILED");
        assertThat(last.payload()).contains("\"reason\":\"COMPENSATION_TIMEOUT\"");
    }

    @Test
    void failTimedOutCompensations_leavesFreshCompensatingSagaUntouched() {
        Instant now = Instant.now();
        SagaState compensating = new SagaState("saga-1", "order-1", SagaStatus.COMPENSATING, now, now);
        sagaStateRepo.save(compensating);

        orchestrator.failTimedOutCompensations();

        SagaState result = sagaStateRepo.findBySagaId("saga-1").orElseThrow();
        assertThat(result.currentStep()).isEqualTo(SagaStatus.COMPENSATING);
        assertThat(outboxPort.events).isEmpty();
    }

    @Test
    void compensate_withMissingSaga_doesNothing() {
        orchestrator.compensate("nonexistent", "ANY");
        assertThat(sagaStateRepo.stored).isNull();
    }

    @Test
    void onInventoryReserved_missingSaga_doesNotThrow() {
        orchestrator.onInventoryReserved("nonexistent");
        assertThat(sagaStateRepo.stored).isNull();
    }

    static class InMemorySagaStateRepository implements SagaStateRepository {
        SagaState stored;

        @Override
        public SagaState save(SagaState sagaState) {
            this.stored = sagaState;
            return sagaState;
        }

        @Override
        public Optional<SagaState> findBySagaId(String sagaId) {
            if (stored != null && stored.sagaId().equals(sagaId)) {
                return Optional.of(stored);
            }
            return Optional.empty();
        }

        @Override
        public Optional<SagaState> findByOrderId(String orderId) {
            if (stored != null && stored.orderId().equals(orderId)) {
                return Optional.of(stored);
            }
            return Optional.empty();
        }

        @Override
        public List<SagaState> findCompensatingUpdatedBefore(Instant cutoff) {
            if (stored != null && stored.currentStep() == SagaStatus.COMPENSATING && stored.updatedAt().isBefore(cutoff)) {
                return List.of(stored);
            }
            return List.of();
        }
    }

    static class InMemoryOutboxPort implements OutboxPort {
        record PublishedEvent(String aggregateType, String aggregateId, String eventType, String payload) {}

        List<PublishedEvent> events = new ArrayList<>();

        @Override
        public void publish(String aggregateType, String aggregateId, String eventType, String payload) {
            events.add(new PublishedEvent(aggregateType, aggregateId, eventType, payload));
        }

        PublishedEvent lastEvent() {
            return events.get(events.size() - 1);
        }
    }
}
