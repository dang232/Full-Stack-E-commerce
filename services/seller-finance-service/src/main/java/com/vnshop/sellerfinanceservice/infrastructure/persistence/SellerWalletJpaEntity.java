package com.vnshop.sellerfinanceservice.infrastructure.persistence;

import com.vnshop.sellerfinanceservice.domain.SellerWallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(schema = "seller_finance_svc", name = "seller_wallets")
public class SellerWalletJpaEntity {
    @Id
    @Column(name = "seller_id")
    private String sellerId;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "pending_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal pendingBalance;

    @Column(name = "total_earned", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalEarned;

    @Column(name = "last_payout_at")
    private Instant lastPayoutAt;

    protected SellerWalletJpaEntity() {
    }

    static SellerWalletJpaEntity fromDomain(SellerWallet wallet) {
        SellerWalletJpaEntity entity = new SellerWalletJpaEntity();
        entity.sellerId = wallet.sellerId();
        entity.availableBalance = wallet.availableBalance();
        entity.pendingBalance = wallet.pendingBalance();
        entity.totalEarned = wallet.totalEarned();
        entity.lastPayoutAt = wallet.lastPayoutAt();
        return entity;
    }

    SellerWallet toDomain() {
        return new SellerWallet(sellerId, availableBalance, pendingBalance, totalEarned, lastPayoutAt);
    }
}
