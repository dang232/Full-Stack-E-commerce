package com.vnshop.orderservice.application.finance;

import com.vnshop.orderservice.domain.finance.CommissionCalculator;
import com.vnshop.orderservice.domain.finance.CommissionTier;
import com.vnshop.orderservice.domain.finance.SellerTransaction;
import com.vnshop.orderservice.domain.finance.SellerWallet;
import com.vnshop.orderservice.domain.finance.port.out.SellerTransactionRepositoryPort;
import com.vnshop.orderservice.domain.finance.port.out.SellerWalletRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class CreditWalletUseCase {
    private final SellerWalletRepositoryPort walletRepository;
    private final SellerTransactionRepositoryPort transactionRepository;
    private final CommissionCalculator commissionCalculator;

    public CreditWalletUseCase(
            SellerWalletRepositoryPort walletRepository,
            SellerTransactionRepositoryPort transactionRepository,
            CommissionCalculator commissionCalculator
    ) {
        this.walletRepository = Objects.requireNonNull(walletRepository, "walletRepository is required");
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository is required");
        this.commissionCalculator = Objects.requireNonNull(commissionCalculator, "commissionCalculator is required");
    }

    @Transactional
    public CreditWalletResult credit(String sellerId, BigDecimal originalAmount, CommissionTier tier, String idempotencyKey) {
        requireNonBlank(sellerId, "sellerId");
        requireNonBlank(idempotencyKey, "idempotencyKey");
        SellerWallet wallet = walletRepository.findBySellerId(sellerId).orElseGet(() -> new SellerWallet(sellerId));
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return new CreditWalletResult(wallet, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        CommissionCalculator.CommissionBreakdown breakdown = commissionCalculator.calculate(originalAmount, tier);
        wallet.creditOrderPayment(breakdown.sellerNet(), breakdown.commission());
        SellerWallet savedWallet = walletRepository.save(wallet);
        transactionRepository.save(SellerTransaction.orderPayment(sellerId, breakdown.sellerNet(), breakdown.commission(), savedWallet.availableBalance(), idempotencyKey, Instant.now()));
        return new CreditWalletResult(savedWallet, breakdown.commission(), breakdown.sellerNet());
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    public record CreditWalletResult(SellerWallet wallet, BigDecimal commission, BigDecimal sellerNet) {
    }
}
