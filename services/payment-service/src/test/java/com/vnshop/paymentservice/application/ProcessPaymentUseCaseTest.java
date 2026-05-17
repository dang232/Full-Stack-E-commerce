package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.JournalEntry;
import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentIdempotencyKey;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import com.vnshop.paymentservice.domain.port.out.PaymentIdempotencyKeyRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.application.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessPaymentUseCaseTest {

    @Test
    void cachesResponseForRepeatedKeyAndIdenticalBody() {
        InMemoryPayments payments = new InMemoryPayments();
        CountingGateway gateway = new CountingGateway();
        InMemoryIdempotencyKeys keys = new InMemoryIdempotencyKeys();
        ProcessPaymentUseCase useCase = newUseCase(payments, gateway, keys);

        ProcessPaymentCommand cmd = new ProcessPaymentCommand("ORDER-1", "BUYER-1",
                new BigDecimal("100000.00"), PaymentMethodInput.VNPAY, "key-A");

        Payment first = useCase.process(cmd);
        Payment second = useCase.process(cmd);

        assertThat(gateway.calls).isEqualTo(1);
        assertThat(payments.saved).hasSize(1);
        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(keys.store).containsKey("key-A");
    }

    @Test
    void rejectsKeyReuseWithDifferentBody() {
        InMemoryPayments payments = new InMemoryPayments();
        CountingGateway gateway = new CountingGateway();
        InMemoryIdempotencyKeys keys = new InMemoryIdempotencyKeys();
        ProcessPaymentUseCase useCase = newUseCase(payments, gateway, keys);

        useCase.process(new ProcessPaymentCommand("ORDER-1", "BUYER-1",
                new BigDecimal("100000.00"), PaymentMethodInput.VNPAY, "key-B"));

        assertThatThrownBy(() -> useCase.process(new ProcessPaymentCommand("ORDER-2", "BUYER-1",
                new BigDecimal("100000.00"), PaymentMethodInput.VNPAY, "key-B")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        assertThat(gateway.calls).isEqualTo(1);
    }

    @Test
    void treatsDistinctKeysAsIndependentRequests() {
        InMemoryPayments payments = new InMemoryPayments();
        CountingGateway gateway = new CountingGateway();
        InMemoryIdempotencyKeys keys = new InMemoryIdempotencyKeys();
        ProcessPaymentUseCase useCase = newUseCase(payments, gateway, keys);

        useCase.process(new ProcessPaymentCommand("ORDER-1", "BUYER-1",
                new BigDecimal("100000.00"), PaymentMethodInput.VNPAY, "key-1"));
        useCase.process(new ProcessPaymentCommand("ORDER-2", "BUYER-1",
                new BigDecimal("100000.00"), PaymentMethodInput.VNPAY, "key-2"));

        assertThat(gateway.calls).isEqualTo(2);
        assertThat(payments.saved).hasSize(2);
        assertThat(keys.store).containsOnlyKeys("key-1", "key-2");
    }

    @Test
    void skipsIdempotencyStoreWhenKeyAbsent() {
        InMemoryPayments payments = new InMemoryPayments();
        CountingGateway gateway = new CountingGateway();
        InMemoryIdempotencyKeys keys = new InMemoryIdempotencyKeys();
        ProcessPaymentUseCase useCase = newUseCase(payments, gateway, keys);

        useCase.process(new ProcessPaymentCommand("ORDER-1", "BUYER-1",
                new BigDecimal("50000.00"), PaymentMethodInput.COD, null));
        useCase.process(new ProcessPaymentCommand("ORDER-1", "BUYER-1",
                new BigDecimal("50000.00"), PaymentMethodInput.COD, "   "));

        assertThat(gateway.calls).isEqualTo(2);
        assertThat(keys.store).isEmpty();
    }

    /**
     * Issue 1 fix: when the ledger insert fails inside the transactional block, the whole
     * unit of work must roll back. Spring's TransactionTemplate guarantees the payment row
     * rollback at the DB layer; what we verify here is that <em>no idempotency key is
     * persisted</em>, so a retry with the same key will re-attempt rather than returning a
     * stale "success" pointer.
     */
    @Test
    void doesNotSaveIdempotencyKeyWhenLedgerInsertFails() {
        PaymentRepositoryPort payments = mock(PaymentRepositoryPort.class);
        PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
        LedgerService ledgerService = mock(LedgerService.class);
        PaymentIdempotencyKeyRepositoryPort keys = mock(PaymentIdempotencyKeyRepositoryPort.class);

        when(keys.findByKey("key-fail")).thenReturn(Optional.empty());
        when(gateway.processPayment(any())).thenReturn(
                new PaymentGatewayPort.GatewayPaymentResult(PaymentStatus.COMPLETED, "TXN-FAIL")
        );
        when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("ledger DB down")).when(ledgerService).recordPayment(any(Payment.class));

        ProcessPaymentUseCase useCase = new ProcessPaymentUseCase(
                payments, gateway, ledgerService, keys, recordingTransactionTemplate(),
                Clock.fixed(Instant.parse("2026-05-17T10:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> useCase.process(new ProcessPaymentCommand(
                "ORDER-FAIL", "BUYER-1", new BigDecimal("100000.00"), PaymentMethodInput.VNPAY, "key-fail"
        ))).isInstanceOf(RuntimeException.class).hasMessageContaining("ledger DB down");

        // Idempotency key NEVER reached the store. A retry with "key-fail" will re-enter the
        // use case from the top — the gateway will be called again, which is the correct
        // recovery behavior given that the previous charge was orphaned (logged) and is
        // expected to be picked up by the reconciliation worker.
        verify(keys, never()).save(any());
    }

    /**
     * Issue 1 fix: the gateway side-effect must happen BEFORE we open the transaction.
     * Charges are not rollback-able by deleting a row, so we charge first, then atomically
     * persist the payment + ledger + idempotency key.
     */
    @Test
    void callsGatewayBeforeOpeningTransaction() {
        PaymentRepositoryPort payments = mock(PaymentRepositoryPort.class);
        PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
        LedgerService ledgerService = mock(LedgerService.class);
        PaymentIdempotencyKeyRepositoryPort keys = mock(PaymentIdempotencyKeyRepositoryPort.class);
        RecordingTransactionManager txManager = new RecordingTransactionManager();

        when(keys.findByKey("key-order")).thenReturn(Optional.empty());
        when(gateway.processPayment(any())).thenReturn(
                new PaymentGatewayPort.GatewayPaymentResult(PaymentStatus.COMPLETED, "TXN-ORDER")
        );
        when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProcessPaymentUseCase useCase = new ProcessPaymentUseCase(
                payments, gateway, ledgerService, keys,
                new TransactionTemplate(txManager.proxy()),
                Clock.fixed(Instant.parse("2026-05-17T10:00:00Z"), ZoneOffset.UTC)
        );

        useCase.process(new ProcessPaymentCommand(
                "ORDER-ORD", "BUYER-1", new BigDecimal("100000.00"), PaymentMethodInput.VNPAY, "key-order"
        ));

        InOrder order = inOrder(gateway, txManager.proxy(), payments, ledgerService, keys);
        order.verify(gateway).processPayment(any());
        order.verify(txManager.proxy()).getTransaction(any());
        order.verify(payments).save(any());
        order.verify(ledgerService).recordPayment(any(Payment.class));
        order.verify(keys).save(any());
        order.verify(txManager.proxy()).commit(any());
    }

    private static ProcessPaymentUseCase newUseCase(InMemoryPayments payments, CountingGateway gateway, InMemoryIdempotencyKeys keys) {
        return new ProcessPaymentUseCase(payments, gateway, new LedgerService(new NoopLedgerRepository()), keys,
                noopTransactionTemplate(),
                Clock.fixed(Instant.parse("2026-05-17T10:00:00Z"), ZoneOffset.UTC));
    }

    private static TransactionTemplate noopTransactionTemplate() {
        return new TransactionTemplate(new NoopPlatformTransactionManager());
    }

    /**
     * Same as {@link #noopTransactionTemplate()} but explicit about the rollback path —
     * useful when a test wants to assert that the rollback branch is exercised. The no-op
     * manager doesn't actually undo in-memory writes, but TransactionTemplate's contract
     * guarantees the callback is wrapped in begin/(commit|rollback), so the rollback path
     * means "no further writes were issued after the throw" — which is exactly what we
     * assert via mock verification.
     */
    private static TransactionTemplate recordingTransactionTemplate() {
        return new TransactionTemplate(new NoopPlatformTransactionManager());
    }

    private static final class NoopPlatformTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) { }

        @Override
        public void rollback(TransactionStatus status) { }
    }

    /**
     * Wraps a real {@link NoopPlatformTransactionManager} in a Mockito spy so the InOrder
     * assertion can verify {@code getTransaction} was invoked between the gateway call and
     * the persistence calls. Spying gives us the recording behavior without re-implementing
     * the manager.
     */
    private static final class RecordingTransactionManager {
        private final PlatformTransactionManager delegate = mock(PlatformTransactionManager.class);

        RecordingTransactionManager() {
            try {
                when(delegate.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        PlatformTransactionManager proxy() {
            return delegate;
        }
    }

    private static final class InMemoryPayments implements PaymentRepositoryPort {
        private final List<Payment> saved = new ArrayList<>();
        private final Map<UUID, Payment> byId = new HashMap<>();

        @Override
        public Payment save(Payment payment) {
            saved.add(payment);
            byId.put(payment.paymentId(), payment);
            return payment;
        }

        @Override
        public Optional<Payment> findById(UUID paymentId) {
            return Optional.ofNullable(byId.get(paymentId));
        }

        @Override
        public Optional<Payment> findByOrderId(String orderId) {
            return saved.stream().filter(p -> p.orderId().equals(orderId)).findFirst();
        }

        @Override
        public List<Payment> findByStatus(PaymentStatus status) {
            return saved.stream().filter(p -> p.status() == status).toList();
        }
    }

    private static final class CountingGateway implements PaymentGatewayPort {
        private int calls;

        @Override
        public GatewayPaymentResult processPayment(Payment payment) {
            calls++;
            return new GatewayPaymentResult(PaymentStatus.PENDING, "TXN-" + calls);
        }

        @Override
        public PaymentStatus getStatus(String paymentId) {
            return PaymentStatus.PENDING;
        }
    }

    private static final class InMemoryIdempotencyKeys implements PaymentIdempotencyKeyRepositoryPort {
        private final Map<String, PaymentIdempotencyKey> store = new HashMap<>();

        @Override
        public Optional<PaymentIdempotencyKey> findByKey(String key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public PaymentIdempotencyKey save(PaymentIdempotencyKey key) {
            store.put(key.key(), key);
            return key;
        }
    }

    private static final class NoopLedgerRepository implements LedgerRepositoryPort {
        @Override
        public List<LedgerEntry> append(JournalEntry journalEntry) {
            return List.of();
        }

        @Override
        public List<LedgerEntry> findByOrderId(String orderId) {
            return List.of();
        }

        @Override
        public List<LedgerEntry> findByJournalId(UUID journalId) {
            return List.of();
        }
    }

    @SuppressWarnings("unused")
    private static Payment paymentSeed() {
        return new Payment(UUID.randomUUID(), "ORDER-1", "BUYER-1", new BigDecimal("100000.00"),
                PaymentMethod.VNPAY, PaymentStatus.PENDING, null, Instant.parse("2026-05-17T10:00:00Z"));
    }
}
