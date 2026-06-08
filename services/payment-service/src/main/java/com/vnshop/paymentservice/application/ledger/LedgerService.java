package com.vnshop.paymentservice.application.ledger;

import com.vnshop.paymentservice.application.LedgerPaymentCommand;
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

    private final String currency;
    private final String buyerAccount;
    private final String clearingAccount;
    private final LedgerRepositoryPort ledgerRepositoryPort;

    /** Spring-wired constructor — values come from application properties. */
    public LedgerService(
            LedgerRepositoryPort ledgerRepositoryPort,
            String currency,
            String buyerAccount,
            String clearingAccount) {
        this.ledgerRepositoryPort = Objects.requireNonNull(ledgerRepositoryPort, "ledgerRepositoryPort is required");
        this.currency = Objects.requireNonNull(currency, "currency is required");
        this.buyerAccount = Objects.requireNonNull(buyerAccount, "buyerAccount is required");
        this.clearingAccount = Objects.requireNonNull(clearingAccount, "clearingAccount is required");
    }

    /** Convenience constructor using production defaults — used in tests and legacy wiring. */
    public LedgerService(LedgerRepositoryPort ledgerRepositoryPort) {
        this(ledgerRepositoryPort, "VND", "buyer_cash", "payment_clearing");
    }

    public List<LedgerEntry> recordPayment(Payment payment) {
        Objects.requireNonNull(payment, "payment is required");
        return recordPayment(new LedgerPaymentCommand(payment.transactionRef(), payment.orderId(), payment.amount()));
    }

    public List<LedgerEntry> recordPayment(LedgerPaymentCommand command) {
        return ledgerRepositoryPort.append(paymentJournal(command));
    }

    public JournalEntry paymentJournal(LedgerPaymentCommand command) {
        Objects.requireNonNull(command.amount(), "amount is required");
        if (command.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return JournalEntry.posted(
                command.transactionId(),
                command.orderId(),
                "Payment captured",
                List.of(
                        new LedgerPosting(clearingAccount, LedgerPostingType.DEBIT, command.amount(), currency),
                        new LedgerPosting(buyerAccount, LedgerPostingType.CREDIT, command.amount(), currency)
                ));
    }

    public List<LedgerEntry> reverseJournal(JournalEntry originalJournal, String reversalTransactionId) {
        JournalEntry reversal = originalJournal.reversal(reversalTransactionId, "Reversal for journal " + originalJournal.journalId());
        return ledgerRepositoryPort.append(reversal);
    }
}
