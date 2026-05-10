package com.vnshop.paymentservice.infrastructure.ledger;

import com.vnshop.paymentservice.domain.LedgerEntry;
import com.vnshop.paymentservice.domain.port.out.LedgerRepositoryPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Service
public class LedgerService {
    private static final String CURRENCY = "VND";
    private static final String STATUS_POSTED = "POSTED";
    private static final String BUYER_WALLET = "buyer_wallet";
    private static final String ESCROW = "escrow";
    private static final String SELLER_WALLET = "seller_wallet";

    private final LedgerRepositoryPort ledgerRepositoryPort;

    public LedgerService(LedgerRepositoryPort ledgerRepositoryPort) {
        this.ledgerRepositoryPort = Objects.requireNonNull(ledgerRepositoryPort, "ledgerRepositoryPort is required");
    }

    public void recordPayment(String transactionId, String orderId, BigDecimal amount) {
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        Instant timestamp = Instant.now();
        LedgerEntry buyerToEscrow = new LedgerEntry(
                transactionId,
                orderId,
                BUYER_WALLET,
                ESCROW,
                amount,
                CURRENCY,
                timestamp,
                STATUS_POSTED,
                "Payment captured from buyer wallet into escrow");
        LedgerEntry escrowToSeller = new LedgerEntry(
                transactionId,
                orderId,
                ESCROW,
                SELLER_WALLET,
                amount,
                CURRENCY,
                timestamp,
                STATUS_POSTED,
                "Payment settled from escrow to seller wallet");

        validateBalanced(buyerToEscrow, escrowToSeller);
        ledgerRepositoryPort.save(buyerToEscrow);
        ledgerRepositoryPort.save(escrowToSeller);
    }

    private void validateBalanced(LedgerEntry debitEntry, LedgerEntry creditEntry) {
        if (debitEntry.amount().compareTo(creditEntry.amount()) != 0) {
            throw new IllegalStateException("ledger debit and credit amounts must match");
        }
    }
}
