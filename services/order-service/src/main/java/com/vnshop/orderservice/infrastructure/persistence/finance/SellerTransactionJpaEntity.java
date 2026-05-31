package com.vnshop.orderservice.infrastructure.persistence.finance;

import com.vnshop.orderservice.domain.finance.SellerTransaction;
import com.vnshop.orderservice.domain.finance.SellerTransactionType;
import com.vnshop.orderservice.infrastructure.persistence.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "order_svc", name = "seller_transactions")
@Getter
@Setter
public class SellerTransactionJpaEntity extends BaseJpaEntity {
    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private SellerTransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 512)
    private String idempotencyKey;

    @Column(name = "transaction_created_at", nullable = false)
    private Instant transactionCreatedAt;

    protected SellerTransactionJpaEntity() {
    }

    static SellerTransactionJpaEntity fromDomain(SellerTransaction transaction) {
        SellerTransactionJpaEntity entity = new SellerTransactionJpaEntity();
        entity.transactionId = transaction.transactionId();
        entity.sellerId = transaction.sellerId();
        entity.type = transaction.type();
        entity.amount = transaction.amount();
        entity.feeAmount = transaction.feeAmount();
        entity.balanceAfter = transaction.balanceAfter();
        entity.idempotencyKey = transaction.idempotencyKey();
        entity.transactionCreatedAt = transaction.createdAt();
        return entity;
    }

    SellerTransaction toDomain() {
        return new SellerTransaction(
                transactionId, sellerId, type, amount, feeAmount, balanceAfter, idempotencyKey, transactionCreatedAt);
    }
}
