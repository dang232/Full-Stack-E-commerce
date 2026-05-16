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

    private static ProcessPaymentUseCase newUseCase(InMemoryPayments payments, CountingGateway gateway, InMemoryIdempotencyKeys keys) {
        return new ProcessPaymentUseCase(payments, gateway, new LedgerService(new NoopLedgerRepository()), keys,
                Clock.fixed(Instant.parse("2026-05-17T10:00:00Z"), ZoneOffset.UTC));
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
