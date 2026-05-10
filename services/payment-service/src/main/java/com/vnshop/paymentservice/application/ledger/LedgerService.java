package com.vnshop.paymentservice.application.ledger;

import com.vnshop.paymentservice.domain.JournalEntry;
import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.LedgerPosting;
import com.vnshop.paymentservice.domain.LedgerPostingType;
import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public class LedgerService {
    private static final String CURRENCY = "VND";
    private static final String BUYER_CASH = "buyer_cash";
    private static final String PAYMENT_CLEARING = "payment_clearing";

    private final LedgerRepositoryPort ledgerRepositoryPort;

    public LedgerService(LedgerRepositoryPort ledgerRepositoryPort) {
        this.ledgerRepositoryPort = Objects.requireNonNull(ledgerRepositoryPort, "ledgerRepositoryPort is required");
    }

    public List<LedgerEntry> recordPayment(Payment payment) {
        Objects.requireNonNull(payment, "payment is required");
        return recordPayment(payment.transactionRef(), payment.orderId(), payment.amount());
    }

    public List<LedgerEntry> recordPayment(String transactionId, String orderId, BigDecimal amount) {
        return ledgerRepositoryPort.append(paymentJournal(transactionId, orderId, amount));
    }

    public JournalEntry paymentJournal(String transactionId, String orderId, BigDecimal amount) {
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return JournalEntry.posted(
                transactionId,
                orderId,
                "Payment captured",
                List.of(
                        new LedgerPosting(PAYMENT_CLEARING, LedgerPostingType.DEBIT, amount, CURRENCY),
                        new LedgerPosting(BUYER_CASH, LedgerPostingType.CREDIT, amount, CURRENCY)
                ));
    }

    public List<LedgerEntry> reverseJournal(JournalEntry originalJournal, String reversalTransactionId) {
        JournalEntry reversal = originalJournal.reversal(reversalTransactionId, "Reversal for journal " + originalJournal.journalId());
        return ledgerRepositoryPort.append(reversal);
    }
}
