package com.vnshop.orderservice.application.finance;

import com.vnshop.orderservice.domain.finance.Payout;
import com.vnshop.orderservice.domain.finance.SellerWallet;
import com.vnshop.orderservice.domain.finance.port.out.PayoutRepositoryPort;
import com.vnshop.orderservice.domain.finance.port.out.SellerWalletRepositoryPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class RequestPayoutUseCase {
    private final SellerWalletRepositoryPort walletRepository;
    private final PayoutRepositoryPort payoutRepository;

    public RequestPayoutUseCase(SellerWalletRepositoryPort walletRepository, PayoutRepositoryPort payoutRepository) {
        this.walletRepository = Objects.requireNonNull(walletRepository, "walletRepository is required");
        this.payoutRepository = Objects.requireNonNull(payoutRepository, "payoutRepository is required");
    }

    public Payout request(String sellerId, BigDecimal amount) {
        requireNonBlank(sellerId, "sellerId");
        SellerWallet wallet = walletRepository.findBySellerId(sellerId).orElseThrow(() -> new IllegalArgumentException("wallet not found"));
        wallet.reservePayout(amount);
        walletRepository.save(wallet);
        Payout payout = Payout.pending(sellerId, amount, Instant.now());
        return payoutRepository.save(payout);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
