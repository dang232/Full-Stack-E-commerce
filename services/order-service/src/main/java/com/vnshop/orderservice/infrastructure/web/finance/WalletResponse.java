package com.vnshop.orderservice.infrastructure.web.finance;

import com.vnshop.orderservice.domain.finance.SellerWallet;
import java.math.BigDecimal;
import java.time.Instant;

public record WalletResponse(
        String sellerId,
        BigDecimal availableBalance,
        BigDecimal pendingBalance,
        BigDecimal totalEarned,
        Instant lastPayoutAt
) {
    static WalletResponse fromDomain(SellerWallet wallet) {
        return new WalletResponse(wallet.sellerId(), wallet.availableBalance(), wallet.pendingBalance(), wallet.totalEarned(), wallet.lastPayoutAt());
    }
}
