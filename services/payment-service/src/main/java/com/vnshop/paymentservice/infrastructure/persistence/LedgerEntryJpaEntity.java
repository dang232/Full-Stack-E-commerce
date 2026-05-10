package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.domain.LedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "debit_account", nullable = false)
    private String debitAccount;

    @Column(name = "credit_account", nullable = false)
    private String creditAccount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "description", length = 1024)
    private String description;

    protected LedgerEntryJpaEntity() {
    }

    public static LedgerEntryJpaEntity fromDomain(LedgerEntry ledgerEntry) {
        LedgerEntryJpaEntity entity = new LedgerEntryJpaEntity();
        entity.transactionId = ledgerEntry.transactionId();
        entity.orderId = ledgerEntry.orderId();
        entity.debitAccount = ledgerEntry.debitAccount();
        entity.creditAccount = ledgerEntry.creditAccount();
        entity.amount = ledgerEntry.amount();
        entity.currency = ledgerEntry.currency();
        entity.createdAt = ledgerEntry.timestamp();
        entity.status = ledgerEntry.status();
        entity.description = ledgerEntry.description();
        return entity;
    }

    public LedgerEntry toDomain() {
        return new LedgerEntry(transactionId, orderId, debitAccount, creditAccount, amount, currency, createdAt, status, description);
    }
}
