package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.Address;
import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.PaymentStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = "order_svc", name = "orders")
public class OrderJpaEntity {
    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "buyer_id", nullable = false)
    private String buyerId;

    @Embedded
    private AddressEmbeddable shippingAddress;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "items_total_amount", nullable = false, precision = 19, scale = 0)),
            @AttributeOverride(name = "currency", column = @Column(name = "items_total_currency", nullable = false, length = 8))
    })
    private MoneyEmbeddable itemsTotal;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "shipping_total_amount", nullable = false, precision = 19, scale = 0)),
            @AttributeOverride(name = "currency", column = @Column(name = "shipping_total_currency", nullable = false, length = 8))
    })
    private MoneyEmbeddable shippingTotal;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "discount_amount", nullable = false, precision = 19, scale = 0)),
            @AttributeOverride(name = "currency", column = @Column(name = "discount_currency", nullable = false, length = 8))
    })
    private MoneyEmbeddable discount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "final_amount", nullable = false, precision = 19, scale = 0)),
            @AttributeOverride(name = "currency", column = @Column(name = "final_currency", nullable = false, length = 8))
    })
    private MoneyEmbeddable finalAmount;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SubOrderJpaEntity> subOrders = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    public static OrderJpaEntity fromDomain(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.id = order.id();
        entity.orderNumber = order.orderNumber();
        entity.buyerId = order.buyerId();
        entity.shippingAddress = AddressEmbeddable.fromDomain(order.shippingAddress());
        entity.itemsTotal = MoneyEmbeddable.fromDomain(order.itemsTotal());
        entity.shippingTotal = MoneyEmbeddable.fromDomain(order.shippingTotal());
        entity.discount = MoneyEmbeddable.fromDomain(order.discount());
        entity.finalAmount = MoneyEmbeddable.fromDomain(order.finalAmount());
        entity.paymentMethod = order.paymentMethod();
        entity.paymentStatus = order.paymentStatus();
        entity.idempotencyKey = order.idempotencyKey();
        entity.createdAt = Instant.now();
        entity.subOrders = order.subOrders().stream()
                .map(subOrder -> SubOrderJpaEntity.fromDomain(subOrder, entity))
                .toList();
        return entity;
    }

    public Order toDomain() {
        return new Order(
                id,
                orderNumber,
                buyerId,
                shippingAddress.toDomain(),
                subOrders.stream().map(SubOrderJpaEntity::toDomain).toList(),
                itemsTotal.toDomain(),
                shippingTotal.toDomain(),
                discount.toDomain(),
                paymentMethod,
                paymentStatus,
                idempotencyKey
        );
    }

    public String id() {
        return id;
    }

    public String buyerId() {
        return buyerId;
    }

    @Embeddable
    public static class AddressEmbeddable {
        @Column(name = "shipping_street", nullable = false)
        private String street;

        @Column(name = "shipping_ward")
        private String ward;

        @Column(name = "shipping_district", nullable = false)
        private String district;

        @Column(name = "shipping_city", nullable = false)
        private String city;

        protected AddressEmbeddable() {
        }

        static AddressEmbeddable fromDomain(Address address) {
            AddressEmbeddable embeddable = new AddressEmbeddable();
            embeddable.street = address.street();
            embeddable.ward = address.ward();
            embeddable.district = address.district();
            embeddable.city = address.city();
            return embeddable;
        }

        Address toDomain() {
            return new Address(street, ward, district, city);
        }
    }

    @Embeddable
    public static class MoneyEmbeddable {
        private BigDecimal amount;

        private String currency;

        protected MoneyEmbeddable() {
        }

        static MoneyEmbeddable fromDomain(Money money) {
            MoneyEmbeddable embeddable = new MoneyEmbeddable();
            embeddable.amount = money.amount();
            embeddable.currency = money.currency();
            return embeddable;
        }

        Money toDomain() {
            return new Money(amount, currency);
        }
    }
}
