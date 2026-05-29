package com.vnshop.sellerfinanceservice.application;

import com.vnshop.sellerfinanceservice.domain.CommissionCalculator;
import com.vnshop.sellerfinanceservice.domain.CommissionTier;
import com.vnshop.sellerfinanceservice.domain.SellerWallet;
import com.vnshop.sellerfinanceservice.domain.port.out.SellerWalletRepositoryPort;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Reverses the seller-net portion of an order amount when a buyer return
 * completes and the gateway refund is issued. Mirrors {@link CreditWalletUseCase}
 * — same commission tiering, opposite sign — so the wallet projection ends up
 * where it started before the order.
 *
 * <p>If no wallet exists for the seller (defensive case — credit must have
 * happened first), the use case returns an empty result rather than fabricating
 * a wallet with a negative balance.
 */
public class RefundWalletUseCase {
    private final SellerWalletRepositoryPort walletRepository;
    private final CommissionCalculator commissionCalculator;

    public RefundWalletUseCase(SellerWalletRepositoryPort walletRepository, CommissionCalculator commissionCalculator) {
        this.walletRepository = Objects.requireNonNull(walletRepository, "walletRepository is required");
        this.commissionCalculator = Objects.requireNonNull(commissionCalculator, "commissionCalculator is required");
    }

    public Optional<RefundWalletResult> refund(String sellerId, BigDecimal orderAmount, CommissionTier tier) {
        requireNonBlank(sellerId, "sellerId");
        CommissionCalculator.CommissionBreakdown breakdown = commissionCalculator.calculate(orderAmount, tier);
        Optional<SellerWallet> maybeWallet = walletRepository.findBySellerId(sellerId);
        if (maybeWallet.isEmpty()) {
            return Optional.empty();
        }
        SellerWallet wallet = maybeWallet.get();
        wallet.debit(breakdown.sellerNet());
        SellerWallet savedWallet = walletRepository.save(wallet);
        return Optional.of(new RefundWalletResult(savedWallet, breakdown.commission(), breakdown.sellerNet()));
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    public record RefundWalletResult(SellerWallet wallet, BigDecimal commission, BigDecimal sellerNet) {
    }
}
