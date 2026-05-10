package com.vnshop.orderservice.domain;

import java.util.List;
import java.util.Objects;

public class SubOrder {
    private final Long id;
    private final String sellerId;
    private final List<OrderItem> items;
    private FulfillmentStatus fulfillmentStatus;
    private Money shippingCost;
    private String shippingMethod;
    private String carrier;
    private String trackingNumber;

    public SubOrder(String sellerId, List<OrderItem> items) {
        this(null, sellerId, items, FulfillmentStatus.PENDING_ACCEPTANCE, Money.ZERO, "STANDARD", null, null);
    }

    public SubOrder(
            Long id,
            String sellerId,
            List<OrderItem> items,
            FulfillmentStatus fulfillmentStatus,
            Money shippingCost,
            String shippingMethod,
            String carrier,
            String trackingNumber
    ) {
        requireNonBlank(sellerId, "sellerId");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        this.id = id;
        this.sellerId = sellerId;
        this.items = List.copyOf(items);
        this.fulfillmentStatus = Objects.requireNonNull(fulfillmentStatus, "fulfillmentStatus is required");
        this.shippingCost = Objects.requireNonNull(shippingCost, "shippingCost is required");
        this.shippingMethod = shippingMethod == null || shippingMethod.isBlank() ? "STANDARD" : shippingMethod;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }

    public Long id() {
        return id;
    }

    public String sellerId() {
        return sellerId;
    }

    public List<OrderItem> items() {
        return items;
    }

    public FulfillmentStatus fulfillmentStatus() {
        return fulfillmentStatus;
    }

    public Money shippingCost() {
        return shippingCost;
    }

    public String shippingMethod() {
        return shippingMethod;
    }

    public String carrier() {
        return carrier;
    }

    public String trackingNumber() {
        return trackingNumber;
    }

    public Money itemsTotal() {
        return items.stream()
                .map(OrderItem::totalPrice)
                .reduce(Money.ZERO, Money::add);
    }

    public void accept() {
        transitionTo(FulfillmentStatus.ACCEPTED);
    }

    public void reject() {
        transitionTo(FulfillmentStatus.REJECTED);
    }

    public void pack() {
        transitionTo(FulfillmentStatus.PACKED);
    }

    public void ship(String carrier, String trackingNumber) {
        requireNonBlank(carrier, "carrier");
        requireNonBlank(trackingNumber, "trackingNumber");
        transitionTo(FulfillmentStatus.SHIPPED);
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }

    public void cancel() {
        if (fulfillmentStatus != FulfillmentStatus.PENDING_ACCEPTANCE
                && fulfillmentStatus != FulfillmentStatus.ACCEPTED) {
            throw new IllegalStateException("cannot cancel from " + fulfillmentStatus);
        }
        fulfillmentStatus = FulfillmentStatus.CANCELLED;
    }

    public void setShippingCost(Money shippingCost) {
        this.shippingCost = Objects.requireNonNull(shippingCost, "shippingCost is required");
    }

    public void setShippingMethod(String shippingMethod) {
        this.shippingMethod = shippingMethod == null || shippingMethod.isBlank() ? "STANDARD" : shippingMethod;
    }

    private void transitionTo(FulfillmentStatus nextStatus) {
        if (!canTransitionTo(nextStatus)) {
            throw new IllegalStateException("cannot transition from " + fulfillmentStatus + " to " + nextStatus);
        }
        fulfillmentStatus = nextStatus;
    }

    private boolean canTransitionTo(FulfillmentStatus nextStatus) {
        return switch (fulfillmentStatus) {
            case PENDING_ACCEPTANCE -> nextStatus == FulfillmentStatus.ACCEPTED || nextStatus == FulfillmentStatus.REJECTED;
            case ACCEPTED -> nextStatus == FulfillmentStatus.PACKED;
            case PACKED -> nextStatus == FulfillmentStatus.SHIPPED;
            case SHIPPED, REJECTED, CANCELLED -> false;
        };
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
