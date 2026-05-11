package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.JournalEntry;
import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.application.ledger.LedgerService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HandleVnpayIpnUseCaseTest {
    @Test
    void updatesPendingVnpayPaymentAfterVerifiedIpnOnly() {
        CapturingPaymentRepository repository = new CapturingPaymentRepository(payment(PaymentStatus.PENDING, null));
        CapturingLedgerRepository ledgerRepository = new CapturingLedgerRepository();
        HandleVnpayIpnUseCase useCase = new HandleVnpayIpnUseCase(repository, new LedgerService(ledgerRepository));

        Payment updated = useCase.applyVerifiedResult(paymentId(), PaymentStatus.COMPLETED, "14123456");

        assertThat(updated.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(updated.transactionRef()).isEqualTo("14123456");
        assertThat(repository.savedPayments).hasSize(1);
        assertThat(ledgerRepository.savedEntries).hasSize(2);
    }

    @Test
    void keepsAlreadyResolvedPaymentIdempotent() {
        CapturingPaymentRepository repository = new CapturingPaymentRepository(payment(PaymentStatus.COMPLETED, "14123456"));
        CapturingLedgerRepository ledgerRepository = new CapturingLedgerRepository();
        HandleVnpayIpnUseCase useCase = new HandleVnpayIpnUseCase(repository, new LedgerService(ledgerRepository));

        Payment existing = useCase.applyVerifiedResult(paymentId(), PaymentStatus.COMPLETED, "14123456");

        assertThat(existing.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(repository.savedPayments).isEmpty();
        assertThat(ledgerRepository.savedEntries).isEmpty();
    }

    private static Payment payment(PaymentStatus status, String transactionRef) {
        return new Payment(paymentId(), "ORDER-1", "BUYER-1", new BigDecimal("120000.00"), PaymentMethod.VNPAY, status, transactionRef, Instant.parse("2026-05-10T09:00:00Z"));
    }

    private static UUID paymentId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private static final class CapturingPaymentRepository implements PaymentRepositoryPort {
        private Payment payment;
        private final List<Payment> savedPayments = new ArrayList<>();

        private CapturingPaymentRepository(Payment payment) {
            this.payment = payment;
        }

        @Override
        public Payment save(Payment payment) {
            this.payment = payment;
            savedPayments.add(payment);
            return payment;
        }

        @Override
        public Optional<Payment> findById(UUID paymentId) {
            return payment.paymentId().equals(paymentId) ? Optional.of(payment) : Optional.empty();
        }

        @Override
        public Optional<Payment> findByOrderId(String orderId) {
            return Optional.empty();
        }

        @Override
        public List<Payment> findByStatus(PaymentStatus status) {
            return List.of();
        }
    }

    private static final class CapturingLedgerRepository implements LedgerRepositoryPort {
        private final List<LedgerEntry> savedEntries = new ArrayList<>();

        @Override
        public List<LedgerEntry> append(JournalEntry journalEntry) {
            List<LedgerEntry> entries = journalEntry.postings().stream()
                    .map(posting -> LedgerEntry.fromJournalPosting(journalEntry, posting))
                    .toList();
            savedEntries.addAll(entries);
            return entries;
        }

        @Override
        public List<LedgerEntry> findByOrderId(String orderId) {
            return savedEntries.stream()
                    .filter(entry -> entry.orderId().equals(orderId))
                    .toList();
        }

        @Override
        public List<LedgerEntry> findByJournalId(UUID journalId) {
            return savedEntries.stream()
                    .filter(entry -> entry.journalId().equals(journalId))
                    .toList();
        }
    }
}

