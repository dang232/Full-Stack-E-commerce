package com.vnshop.paymentservice.application.ledger;

import com.vnshop.paymentservice.application.LedgerPaymentCommand;
import com.vnshop.paymentservice.domain.JournalEntry;
import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.LedgerPosting;
import com.vnshop.paymentservice.domain.LedgerPostingType;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerServiceTest {
    @Test
    void balancedPaymentJournalPostsSuccessfully() {
        CapturingLedgerRepository ledgerRepository = new CapturingLedgerRepository();
        LedgerService ledgerService = new LedgerService(ledgerRepository);

        JournalEntry journalEntry = ledgerService.paymentJournal(new LedgerPaymentCommand("TX-1", "ORDER-1", new BigDecimal("120000.00")));
        List<LedgerEntry> savedEntries = ledgerRepository.append(journalEntry);

        assertThat(savedEntries).hasSize(2);
        assertThat(savedEntries).extracting(LedgerEntry::transactionId).containsOnly("TX-1");
        assertThat(savedEntries).extracting(LedgerEntry::orderId).containsOnly("ORDER-1");
        assertThat(savedEntries).extracting(LedgerEntry::amount).containsOnly(new BigDecimal("120000.00"));
        assertThat(savedEntries).extracting(LedgerEntry::accountId).containsExactly("payment_clearing", "buyer_cash");
        assertThat(savedEntries).extracting(LedgerEntry::postingType).containsExactly(LedgerPostingType.DEBIT, LedgerPostingType.CREDIT);
    }

    @Test
    void unbalancedJournalRejected() {
        assertThatThrownBy(() -> JournalEntry.posted(
                "TX-2",
                "ORDER-2",
                "bad journal",
                List.of(
                        new LedgerPosting("payment_clearing", LedgerPostingType.DEBIT, new BigDecimal("120000.00"), "VND"),
                        new LedgerPosting("buyer_cash", LedgerPostingType.CREDIT, new BigDecimal("119000.00"), "VND")
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("balance per currency");
    }

    @Test
    void reversalCreatesInverseEntriesAndOriginalRemainsUnchanged() {
        CapturingLedgerRepository ledgerRepository = new CapturingLedgerRepository();
        LedgerService ledgerService = new LedgerService(ledgerRepository);
        JournalEntry original = ledgerService.paymentJournal(new LedgerPaymentCommand("TX-3", "ORDER-3", new BigDecimal("50000.00")));
        List<LedgerEntry> originalRows = ledgerRepository.append(original);

        List<LedgerEntry> reversalRows = ledgerService.reverseJournal(original, "REV-TX-3");

        assertThat(originalRows).extracting(LedgerEntry::postingType).containsExactly(LedgerPostingType.DEBIT, LedgerPostingType.CREDIT);
        assertThat(reversalRows).extracting(LedgerEntry::postingType).containsExactly(LedgerPostingType.CREDIT, LedgerPostingType.DEBIT);
        assertThat(reversalRows).extracting(LedgerEntry::reversesJournalId).containsOnly(original.journalId());
        assertThat(ledgerRepository.findByJournalId(original.journalId())).containsExactlyElementsOf(originalRows);
    }

    @Test
    void derivedBalancesAcrossSyntheticOrdersAndRefundsBalancePerCurrency() {
        List<JournalEntry> journals = new ArrayList<>();
        for (int index = 1; index <= 100; index++) {
            BigDecimal amount = new BigDecimal(index + ".00");
            JournalEntry payment = JournalEntry.posted(
                    "TX-" + index,
                    "ORDER-" + index,
                    "Payment captured",
                    List.of(
                            new LedgerPosting("payment_clearing", LedgerPostingType.DEBIT, amount, "VND"),
                            new LedgerPosting("buyer_cash", LedgerPostingType.CREDIT, amount, "VND")
                    ));
            journals.add(payment);
            if (index % 2 == 0) {
                journals.add(payment.reversal("REFUND-TX-" + index, "Refund reversal"));
            }
        }

        List<LedgerEntry> entries = journals.stream()
                .flatMap(journal -> journal.postings().stream().map(posting -> LedgerEntry.fromJournalPosting(journal, posting)))
                .toList();
        Map<String, BigDecimal> debits = totalByCurrency(entries, LedgerPostingType.DEBIT);
        Map<String, BigDecimal> credits = totalByCurrency(entries, LedgerPostingType.CREDIT);

        assertThat(debits).isEqualTo(credits);
    }

    private static Map<String, BigDecimal> totalByCurrency(List<LedgerEntry> entries, LedgerPostingType postingType) {
        return entries.stream()
                .filter(entry -> entry.postingType() == postingType)
                .collect(Collectors.groupingBy(
                        LedgerEntry::currency,
                        Collectors.mapping(LedgerEntry::amount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
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
