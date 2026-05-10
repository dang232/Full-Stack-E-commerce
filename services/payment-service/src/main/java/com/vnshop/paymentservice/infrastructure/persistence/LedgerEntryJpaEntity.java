package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.LedgerPostingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(schema = "payment_svc", name = "ledger_entries")
public class LedgerEntryJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_entry_id")
    private Long ledgerEntryId;

    @Column(name = "journal_id", nullable = false)
    private String journalId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "debit_account", nullable = false)
    private String debitAccount;

    @Column(name = "credit_account", nullable = false)
    private String creditAccount;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "posting_type", nullable = false, length = 16)
    private LedgerPostingType postingType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "reverses_journal_id")
    private String reversesJournalId;

    protected LedgerEntryJpaEntity() {
    }

    public static LedgerEntryJpaEntity fromDomain(LedgerEntry ledgerEntry) {
        LedgerEntryJpaEntity entity = new LedgerEntryJpaEntity();
        entity.journalId = ledgerEntry.journalId();
        entity.transactionId = ledgerEntry.transactionId();
        entity.orderId = ledgerEntry.orderId();
        entity.accountId = ledgerEntry.accountId();
        entity.debitAccount = ledgerEntry.postingType() == LedgerPostingType.DEBIT ? ledgerEntry.accountId() : "-";
        entity.creditAccount = ledgerEntry.postingType() == LedgerPostingType.CREDIT ? ledgerEntry.accountId() : "-";
        entity.status = "POSTED";
        entity.postingType = ledgerEntry.postingType();
        entity.amount = ledgerEntry.amount();
        entity.currency = ledgerEntry.currency();
        entity.createdAt = ledgerEntry.timestamp();
        entity.description = ledgerEntry.description();
        entity.reversesJournalId = ledgerEntry.reversesJournalId();
        return entity;
    }

    public LedgerEntry toDomain() {
        return new LedgerEntry(journalId, transactionId, orderId, accountId, postingType, amount, currency, createdAt, description, reversesJournalId);
    }
}
