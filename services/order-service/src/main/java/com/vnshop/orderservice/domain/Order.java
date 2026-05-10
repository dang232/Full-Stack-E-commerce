package com.vnshop.orderservice.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class Order {
    private static final AtomicInteger ORDER_COUNTER = new AtomicInteger();
    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final String id;
    private final String orderNumber;
    private final String buyerId;
    private final Address shippingAddress;
    private final List<SubOrder> subOrders;
    private Money itemsTotal;
    private Money shippingTotal;
    private Money discount;
    private Money finalAmount;
    private final String paymentMethod;
    private PaymentStatus paymentStatus;
    private final String idempotencyKey;

    public Order(String id, String buyerId, Address shippingAddress, List<SubOrder> subOrders, String idempotencyKey) {
        this(id, generateOrderNumber(), buyerId, shippingAddress, subOrders, Money.ZERO, Money.ZERO, Money.ZERO,
                "COD", PaymentStatus.PENDING, idempotencyKey);
    }

    public Order(
            String id,
            String orderNumber,
            String buyerId,
            Address shippingAddress,
            List<SubOrder> subOrders,
            Money itemsTotal,
            Money shippingTotal,
            Money discount,
            String paymentMethod,
            PaymentStatus paymentStatus,
            String idempotencyKey
    ) {
        requireNonBlank(id, "id");
        requireNonBlank(orderNumber, "orderNumber");
        requireNonBlank(buyerId, "buyerId");
        requireNonBlank(idempotencyKey, "idempotencyKey");
        if (subOrders == null || subOrders.isEmpty()) {
            throw new IllegalArgumentException("subOrders must not be empty");
        }
        this.id = id;
        this.orderNumber = orderNumber;
        this.buyerId = buyerId;
        this.shippingAddress = Objects.requireNonNull(shippingAddress, "shippingAddress is required");
        this.subOrders = List.copyOf(subOrders);
        this.itemsTotal = Objects.requireNonNull(itemsTotal, "itemsTotal is required");
        this.shippingTotal = Objects.requireNonNull(shippingTotal, "shippingTotal is required");
        this.discount = Objects.requireNonNull(discount, "discount is required");
        this.paymentMethod = paymentMethod == null || paymentMethod.isBlank() ? "COD" : paymentMethod;
        this.paymentStatus = Objects.requireNonNull(paymentStatus, "paymentStatus is required");
        this.idempotencyKey = idempotencyKey;
        calculateTotals();
    }

    public static String generateOrderNumber() {
        int sequence = ORDER_COUNTER.updateAndGet(current -> current >= 99999 ? 1 : current + 1);
        return "VNS-" + LocalDate.now().format(ORDER_DATE_FORMAT) + "-" + String.format("%05d", sequence);
    }

    public String id() {
        return id;
    }

    public String orderNumber() {
        return orderNumber;
    }

    public String buyerId() {
        return buyerId;
    }

    public Address shippingAddress() {
        return shippingAddress;
    }

    public List<SubOrder> subOrders() {
        return subOrders;
    }

    public Money itemsTotal() {
        return itemsTotal;
    }

    public Money shippingTotal() {
        return shippingTotal;
    }

    public Money discount() {
        return discount;
    }

    public Money finalAmount() {
        return finalAmount;
    }

    public String paymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus paymentStatus() {
        return paymentStatus;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public void calculateTotals() {
        itemsTotal = subOrders.stream()
                .map(SubOrder::itemsTotal)
                .reduce(Money.ZERO, Money::add);
        shippingTotal = subOrders.stream()
                .map(SubOrder::shippingCost)
                .reduce(Money.ZERO, Money::add);
        Money grossAmount = itemsTotal.add(shippingTotal);
        if (discount.amount().compareTo(grossAmount.amount()) > 0) {
            throw new IllegalArgumentException("discount cannot exceed order total");
        }
        finalAmount = new Money(grossAmount.amount().subtract(discount.amount()), grossAmount.currency());
    }

    public void markPaymentCompleted() {
        paymentStatus = PaymentStatus.COMPLETED;
    }

    public void markPaymentFailed() {
        paymentStatus = PaymentStatus.FAILED;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
