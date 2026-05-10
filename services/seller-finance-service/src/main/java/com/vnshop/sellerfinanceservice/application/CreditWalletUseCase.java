package com.vnshop.sellerfinanceservice.application;

import com.vnshop.sellerfinanceservice.domain.CommissionCalculator;
import com.vnshop.sellerfinanceservice.domain.CommissionTier;
import com.vnshop.sellerfinanceservice.domain.SellerWallet;
import com.vnshop.sellerfinanceservice.domain.port.out.SellerWalletRepositoryPort;

import java.math.BigDecimal;
import java.util.Objects;

public class CreditWalletUseCase {
    private final SellerWalletRepositoryPort walletRepository;
    private final CommissionCalculator commissionCalculator;

    public CreditWalletUseCase(SellerWalletRepositoryPort walletRepository, CommissionCalculator commissionCalculator) {
        this.walletRepository = Objects.requireNonNull(walletRepository, "walletRepository is required");
        this.commissionCalculator = Objects.requireNonNull(commissionCalculator, "commissionCalculator is required");
    }

    public CreditWalletResult credit(String sellerId, BigDecimal orderAmount, CommissionTier tier) {
        requireNonBlank(sellerId, "sellerId");
        CommissionCalculator.CommissionBreakdown breakdown = commissionCalculator.calculate(orderAmount, tier);
        SellerWallet wallet = walletRepository.findBySellerId(sellerId).orElseGet(() -> new SellerWallet(sellerId));
        wallet.credit(breakdown.sellerNet());
        SellerWallet savedWallet = walletRepository.save(wallet);
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
