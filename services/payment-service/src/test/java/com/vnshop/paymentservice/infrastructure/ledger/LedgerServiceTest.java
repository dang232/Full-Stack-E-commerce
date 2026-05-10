package com.vnshop.paymentservice.infrastructure.ledger;

import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerServiceTest {
    @Test
    void recordsBalancedBuyerEscrowAndSellerWalletEntries() {
        CapturingLedgerRepository ledgerRepository = new CapturingLedgerRepository();
        LedgerService ledgerService = new LedgerService(ledgerRepository);

        ledgerService.recordPayment("TX-1", "ORDER-1", new BigDecimal("120000.00"));

        assertThat(ledgerRepository.savedEntries).hasSize(2);
        assertThat(ledgerRepository.savedEntries)
                .extracting(LedgerEntry::transactionId)
                .containsOnly("TX-1");
        assertThat(ledgerRepository.savedEntries)
                .extracting(LedgerEntry::orderId)
                .containsOnly("ORDER-1");
        assertThat(ledgerRepository.savedEntries)
                .extracting(LedgerEntry::amount)
                .containsOnly(new BigDecimal("120000.00"));
        assertThat(ledgerRepository.savedEntries)
                .extracting(LedgerEntry::debitAccount)
                .containsExactly("buyer_wallet", "escrow");
        assertThat(ledgerRepository.savedEntries)
                .extracting(LedgerEntry::creditAccount)
                .containsExactly("escrow", "seller_wallet");
    }

    private static final class CapturingLedgerRepository implements LedgerRepositoryPort {
        private final List<LedgerEntry> savedEntries = new ArrayList<>();

        @Override
        public LedgerEntry save(LedgerEntry ledgerEntry) {
            savedEntries.add(ledgerEntry);
            return ledgerEntry;
        }

        @Override
        public List<LedgerEntry> findByOrderId(String orderId) {
            return savedEntries.stream()
                    .filter(entry -> entry.orderId().equals(orderId))
                    .toList();
        }
    }
}
