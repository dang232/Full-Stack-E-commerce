package com.vnshop.orderservice.application.finance;

import com.vnshop.orderservice.domain.finance.Payout;
import com.vnshop.orderservice.domain.finance.PayoutStatus;
import com.vnshop.orderservice.domain.finance.SellerTransaction;
import com.vnshop.orderservice.domain.finance.SellerWallet;
import com.vnshop.orderservice.domain.finance.port.out.PayoutRepositoryPort;
import com.vnshop.orderservice.domain.finance.port.out.SellerTransactionRepositoryPort;
import com.vnshop.orderservice.domain.finance.port.out.SellerWalletRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class ProcessPayoutUseCase {
    private final SellerWalletRepositoryPort walletRepository;
    private final PayoutRepositoryPort payoutRepository;
    private final SellerTransactionRepositoryPort transactionRepository;

    public ProcessPayoutUseCase(
            SellerWalletRepositoryPort walletRepository,
            PayoutRepositoryPort payoutRepository,
            SellerTransactionRepositoryPort transactionRepository
    ) {
        this.walletRepository = Objects.requireNonNull(walletRepository, "walletRepository is required");
        this.payoutRepository = Objects.requireNonNull(payoutRepository, "payoutRepository is required");
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository is required");
    }

    @Transactional
    public Payout complete(String payoutId) {
        Payout payout = findPayout(payoutId);
        if (payout.status() != PayoutStatus.PENDING) {
            throw new IllegalStateException("payout is not pending");
        }
        SellerWallet wallet = walletRepository.findBySellerId(payout.sellerId()).orElseThrow(() -> new IllegalArgumentException("wallet not found"));
        wallet.completePayout(payout.amount(), Instant.now());
        payout.complete();
        SellerWallet savedWallet = walletRepository.save(wallet);
        transactionRepository.save(SellerTransaction.payout(payout.sellerId(), payout.amount(), savedWallet.availableBalance(), "payout:" + payout.payoutId(), Instant.now()));
        return payoutRepository.save(payout);
    }

    @Transactional
    public Payout fail(String payoutId) {
        Payout payout = findPayout(payoutId);
        if (payout.status() != PayoutStatus.PENDING) {
            throw new IllegalStateException("payout is not pending");
        }
        SellerWallet wallet = walletRepository.findBySellerId(payout.sellerId()).orElseThrow(() -> new IllegalArgumentException("wallet not found"));
        wallet.failPayout(payout.amount());
        payout.fail();
        walletRepository.save(wallet);
        return payoutRepository.save(payout);
    }

    public List<Payout> pending() {
        return payoutRepository.findByStatus(PayoutStatus.PENDING);
    }

    private Payout findPayout(String payoutId) {
        if (payoutId == null || payoutId.isBlank()) {
            throw new IllegalArgumentException("payoutId is required");
        }
        return payoutRepository.findById(UUID.fromString(payoutId)).orElseThrow(() -> new IllegalArgumentException("payout not found"));
    }
}
