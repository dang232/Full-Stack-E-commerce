package com.vnshop.paymentservice.infrastructure.reconciliation;

import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.ReconciliationIssue;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.ReconciliationIssueRepositoryPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class ReconciliationWorker {
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final LedgerRepositoryPort ledgerRepositoryPort;
    private final ReconciliationIssueRepositoryPort reconciliationIssueRepositoryPort;

    public ReconciliationWorker(PaymentRepositoryPort paymentRepositoryPort, LedgerRepositoryPort ledgerRepositoryPort, ReconciliationIssueRepositoryPort reconciliationIssueRepositoryPort) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
        this.ledgerRepositoryPort = Objects.requireNonNull(ledgerRepositoryPort, "ledgerRepositoryPort is required");
        this.reconciliationIssueRepositoryPort = Objects.requireNonNull(reconciliationIssueRepositoryPort, "reconciliationIssueRepositoryPort is required");
    }

    @Scheduled(cron = "0 0 * * * *")
    public void reconcileCompletedPayments() {
        paymentRepositoryPort.findByStatus(PaymentStatus.COMPLETED).forEach(this::reconcilePayment);
    }

    private void reconcilePayment(Payment payment) {
        List<LedgerEntry> ledgerEntries = ledgerRepositoryPort.findByOrderId(payment.orderId());
        BigDecimal expectedAmount = payment.amount().multiply(new BigDecimal("2"));
        BigDecimal actualAmount = ledgerEntries.stream()
                .map(LedgerEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (ledgerEntries.size() != 2 || actualAmount.compareTo(expectedAmount) != 0) {
            reconciliationIssueRepositoryPort.save(new ReconciliationIssue(
                    null,
                    payment.paymentId(),
                    expectedAmount,
                    actualAmount,
                    "Completed payment ledger mismatch for order " + payment.orderId(),
                    Instant.now(),
                    false));
        }
    }
}
