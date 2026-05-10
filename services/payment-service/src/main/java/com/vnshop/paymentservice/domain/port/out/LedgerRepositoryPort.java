package com.vnshop.paymentservice.domain.port.out;

import com.vnshop.paymentservice.domain.JournalEntry;
import com.vnshop.paymentservice.domain.LedgerEntry;

import java.util.List;

public interface LedgerRepositoryPort {
    List<LedgerEntry> append(JournalEntry journalEntry);

    List<LedgerEntry> findByOrderId(String orderId);

    List<LedgerEntry> findByJournalId(String journalId);
}
