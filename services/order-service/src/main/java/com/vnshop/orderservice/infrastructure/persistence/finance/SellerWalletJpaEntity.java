package com.vnshop.orderservice.infrastructure.persistence.finance;

import com.vnshop.orderservice.domain.finance.SellerWallet;
import com.vnshop.orderservice.infrastructure.persistence.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "order_svc", name = "seller_wallets")
@Getter
@Setter
public class SellerWalletJpaEntity extends BaseJpaEntity {
    @Id
    @Column(name = "seller_id")
    private String sellerId;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "pending_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal pendingBalance;

    @Column(name = "total_earned", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalEarned;

    @Column(name = "total_fees", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalFees;

    @Column(name = "total_withdrawn", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalWithdrawn;

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
        entity.totalFees = wallet.totalFees();
        entity.totalWithdrawn = wallet.totalWithdrawn();
        entity.lastPayoutAt = wallet.lastPayoutAt();
        return entity;
    }

    SellerWallet toDomain() {
        return new SellerWallet(sellerId, availableBalance, pendingBalance, totalEarned, totalFees, totalWithdrawn, lastPayoutAt);
    }
}
