package com.vnshop.paymentservice.infrastructure.reconciliation;

import com.vnshop.paymentservice.domain.JournalEntry;
import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.LedgerPostingType;
import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.ReconciliationIssue;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.ReconciliationIssueRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationWorkerTest {
    @Test
    void recordsIssueWhenCompletedPaymentDoesNotHaveTwoMatchingLedgerEntries() {
        CapturingIssueRepository issueRepository = new CapturingIssueRepository();
        Payment payment = new Payment(paymentId(), "ORDER-1", "BUYER-1", new BigDecimal("120000.00"), PaymentMethod.COD, PaymentStatus.COMPLETED, "TX-1", Instant.now());
        ReconciliationWorker worker = new ReconciliationWorker(
                new StaticPaymentRepository(List.of(payment)),
                new StaticLedgerRepository(List.of(oneEntry(payment))),
                issueRepository);

        worker.reconcileCompletedPayments();

        assertThat(issueRepository.savedIssues).hasSize(1);
        ReconciliationIssue issue = issueRepository.savedIssues.get(0);
        assertThat(issue.paymentId()).isEqualTo(paymentId());
        assertThat(issue.expectedAmount()).isEqualByComparingTo(new BigDecimal("240000.00"));
        assertThat(issue.actualAmount()).isEqualByComparingTo(new BigDecimal("120000.00"));
        assertThat(issue.resolved()).isFalse();
    }

    private static LedgerEntry oneEntry(Payment payment) {
        return new LedgerEntry(UUID.fromString("00000000-0000-0000-0000-000000000101"), payment.transactionRef(), payment.orderId(), "payment_clearing", LedgerPostingType.DEBIT, payment.amount(), "VND", Instant.now(), "partial", null);
    }

    private static UUID paymentId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private record StaticPaymentRepository(List<Payment> completedPayments) implements PaymentRepositoryPort {
        @Override
        public Payment save(Payment payment) {
            return payment;
        }

        @Override
        public Optional<Payment> findById(UUID paymentId) {
            return Optional.empty();
        }

        @Override
        public Optional<Payment> findByOrderId(String orderId) {
            return Optional.empty();
        }

        @Override
        public List<Payment> findByStatus(PaymentStatus status) {
            return completedPayments;
        }
    }

    private record StaticLedgerRepository(List<LedgerEntry> entries) implements LedgerRepositoryPort {
        @Override
        public List<LedgerEntry> append(JournalEntry journalEntry) {
            return List.of();
        }

        @Override
        public List<LedgerEntry> findByOrderId(String orderId) {
            return entries;
        }

        @Override
        public List<LedgerEntry> findByJournalId(UUID journalId) {
            return entries.stream()
                    .filter(entry -> entry.journalId().equals(journalId))
                    .toList();
        }
    }

    private static final class CapturingIssueRepository implements ReconciliationIssueRepositoryPort {
        private final List<ReconciliationIssue> savedIssues = new ArrayList<>();

        @Override
        public ReconciliationIssue save(ReconciliationIssue issue) {
            savedIssues.add(issue);
            return issue;
        }
    }
}
