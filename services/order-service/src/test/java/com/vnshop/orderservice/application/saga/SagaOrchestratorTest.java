package com.vnshop.orderservice.application.saga;

import com.vnshop.orderservice.domain.saga.SagaStatus;
import com.vnshop.orderservice.domain.saga.SagaState;
import com.vnshop.orderservice.domain.port.out.SagaStateRepository;
import com.vnshop.orderservice.infrastructure.outbox.OutboxEventJpaEntity;
import com.vnshop.orderservice.infrastructure.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SagaOrchestratorTest {
    private SagaOrchestrator orchestrator;
    private InMemorySagaStateRepository sagaStateRepo;
    private InMemoryOutboxEventRepository outboxEventRepo;

    @BeforeEach
    void setUp() {
        sagaStateRepo = new InMemorySagaStateRepository();
        outboxEventRepo = new InMemoryOutboxEventRepository();
        orchestrator = new SagaOrchestrator(sagaStateRepo, outboxEventRepo);
    }

    @Test
    void startOrderSaga_createsSagaState_withSTARTEDstatus() {
        SagaState saga = orchestrator.startOrderSaga("order-1");

        assertThat(saga.orderId()).isEqualTo("order-1");
        assertThat(saga.currentStep()).isEqualTo(SagaStatus.STARTED);
        assertThat(saga.sagaId()).isNotBlank();
        assertThat(saga.createdAt()).isNotNull();
        assertThat(sagaStateRepo.stored).isNotNull();
        assertThat(outboxEventRepo.lastSaved.getEventType()).isEqualTo("SAGA_STARTED");
    }

    @Test
    void happyPath_allStepsComplete_sagaReachesSHIPPING_CREATED() {
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
        assertThat(afterShipping.currentStep()).isEqualTo(SagaStatus.SHIPPING_CREATED);

        assertThat(outboxEventRepo.events).hasSize(5); // 1 start + 3 steps + 1 complete
    }

    @Test
    void compensate_transitionsTo_COMPENSATING_then_FAILED() {
        SagaState started = orchestrator.startOrderSaga("order-1");
        String sagaId = started.sagaId();

        orchestrator.compensate(sagaId, "PAYMENT_CHARGE");

        SagaState result = sagaStateRepo.findBySagaId(sagaId).orElseThrow();
        assertThat(result.currentStep()).isEqualTo(SagaStatus.FAILED);
        assertThat(outboxEventRepo.events.get(outboxEventRepo.events.size() - 1).getEventType()).isEqualTo("SAGA_COMPENSATING");
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

    // In-memory implementations for testing
    static class InMemorySagaStateRepository implements SagaStateRepository {
        SagaState stored;

        @Override
        public SagaState save(SagaState sagaState) {
            this.stored = sagaState;
            return sagaState;
        }

        @Override
        public Optional<SagaState> findBySagaId(String sagaId) {
            if (stored != null && stored.sagaId().equals(sagaId)) return Optional.of(stored);
            return Optional.empty();
        }

        @Override
        public Optional<SagaState> findByOrderId(String orderId) {
            if (stored != null && stored.orderId().equals(orderId)) return Optional.of(stored);
            return Optional.empty();
        }
    }

    static class InMemoryOutboxEventRepository extends OutboxEventRepository {
        OutboxEventJpaEntity lastSaved;
        List<OutboxEventJpaEntity> events = new ArrayList<>();

        InMemoryOutboxEventRepository() {
            super(null);
        }

        @Override
        public OutboxEventJpaEntity save(OutboxEventJpaEntity event) {
            this.lastSaved = event;
            this.events.add(event);
            return event;
        }
    }
}
