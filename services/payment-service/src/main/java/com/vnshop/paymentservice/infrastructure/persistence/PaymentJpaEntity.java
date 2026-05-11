package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.infrastructure.persistence.BaseJpaEntity;
import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(schema = "payment_svc", name = "payments")
public class PaymentJpaEntity extends BaseJpaEntity {
    @Id
    @Column(name = "payment_id", nullable = false, columnDefinition = "uuid")
    private UUID paymentId;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "buyer_id", nullable = false)
    private String buyerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 32)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "transaction_ref")
    private String transactionRef;


    protected PaymentJpaEntity() {
    }

    public static PaymentJpaEntity fromDomain(Payment payment) {
        PaymentJpaEntity entity = new PaymentJpaEntity();
        entity.paymentId = payment.paymentId();
        entity.orderId = payment.orderId();
        entity.buyerId = payment.buyerId();
        entity.amount = payment.amount();
        entity.method = payment.method();
        entity.status = payment.status();
        entity.transactionRef = payment.transactionRef();
        return entity;
    }

    public Payment toDomain() {
        return new Payment(paymentId, orderId, buyerId, amount, method, status, transactionRef, getCreatedAt());
    }
}
