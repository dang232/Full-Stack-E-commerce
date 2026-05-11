package com.vnshop.paymentservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class LedgerEntry {
    private final UUID journalId;
    private final String transactionId;
    private final String orderId;
    private final String accountId;
    private final LedgerPostingType postingType;
    private final BigDecimal amount;
    private final String currency;
    private final Instant timestamp;
    private final String description;
    private final UUID reversesJournalId;

    public LedgerEntry(UUID journalId, String transactionId, String orderId, String accountId, LedgerPostingType postingType, BigDecimal amount, String currency, Instant timestamp, String description, UUID reversesJournalId) {
        this.journalId = Objects.requireNonNull(journalId, "journalId is required");
        this.transactionId = requireNonBlank(transactionId, "transactionId");
        this.orderId = requireNonBlank(orderId, "orderId");
        this.accountId = requireNonBlank(accountId, "accountId");
        this.postingType = Objects.requireNonNull(postingType, "postingType is required");
        this.amount = Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.currency = requireNonBlank(currency, "currency");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp is required");
        this.description = description;
        this.reversesJournalId = reversesJournalId;
    }

    public static LedgerEntry fromJournalPosting(JournalEntry journalEntry, LedgerPosting posting) {
        return new LedgerEntry(
                journalEntry.journalId(),
                journalEntry.transactionId(),
                journalEntry.orderId(),
                posting.accountId(),
                posting.type(),
                posting.amount(),
                posting.currency(),
                journalEntry.postedAt(),
                journalEntry.description(),
                journalEntry.reversesJournalId());
    }

    public UUID journalId() {
        return journalId;
    }

    public String transactionId() {
        return transactionId;
    }

    public String orderId() {
        return orderId;
    }

    public String accountId() {
        return accountId;
    }

    public LedgerPostingType postingType() {
        return postingType;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String description() {
        return description;
    }

    public UUID reversesJournalId() {
        return reversesJournalId;
    }

    public String debitAccount() {
        return postingType == LedgerPostingType.DEBIT ? accountId : null;
    }

    public String creditAccount() {
        return postingType == LedgerPostingType.CREDIT ? accountId : null;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
