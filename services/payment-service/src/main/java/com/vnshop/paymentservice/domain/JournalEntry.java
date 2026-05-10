package com.vnshop.paymentservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public record JournalEntry(
        String journalId,
        String transactionId,
        String orderId,
        Instant postedAt,
        String description,
        String reversesJournalId,
        List<LedgerPosting> postings
) {
    public JournalEntry {
        journalId = requireNonBlank(journalId, "journalId");
        transactionId = requireNonBlank(transactionId, "transactionId");
        orderId = requireNonBlank(orderId, "orderId");
        postedAt = Objects.requireNonNull(postedAt, "postedAt is required");
        postings = List.copyOf(Objects.requireNonNull(postings, "postings are required"));
        if (postings.size() < 2) {
            throw new IllegalArgumentException("journal requires at least two postings");
        }
        validateBalancedByCurrency(postings);
    }

    public static JournalEntry posted(String transactionId, String orderId, String description, List<LedgerPosting> postings) {
        return new JournalEntry(UUID.randomUUID().toString(), transactionId, orderId, Instant.now(), description, null, postings);
    }

    public JournalEntry reversal(String reversalTransactionId, String description) {
        List<LedgerPosting> reversedPostings = postings.stream()
                .map(LedgerPosting::reverse)
                .toList();
        return new JournalEntry(UUID.randomUUID().toString(), reversalTransactionId, orderId, Instant.now(), description, journalId, reversedPostings);
    }

    private static void validateBalancedByCurrency(List<LedgerPosting> postings) {
        Map<String, BigDecimal> debitTotals = totalsByCurrency(postings, LedgerPostingType.DEBIT);
        Map<String, BigDecimal> creditTotals = totalsByCurrency(postings, LedgerPostingType.CREDIT);
        if (!debitTotals.keySet().equals(creditTotals.keySet())) {
            throw new IllegalArgumentException("journal debits and credits must use same currencies");
        }
        debitTotals.forEach((currency, debitTotal) -> {
            BigDecimal creditTotal = creditTotals.get(currency);
            if (debitTotal.compareTo(creditTotal) != 0) {
                throw new IllegalArgumentException("journal debits and credits must balance per currency");
            }
        });
    }

    private static Map<String, BigDecimal> totalsByCurrency(List<LedgerPosting> postings, LedgerPostingType type) {
        return postings.stream()
                .filter(posting -> posting.type() == type)
                .collect(Collectors.groupingBy(
                        LedgerPosting::currency,
                        Collectors.mapping(LedgerPosting::amount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
