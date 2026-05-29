package com.vnshop.orderservice.domain;

/**
 * Value object grouping shipping-related fields on a sub-order.
 */
public record ShippingInfo(
    Money shippingCost,
    String shippingMethod,
    String carrier,
    String trackingNumber
) {
    public static final ShippingInfo EMPTY = new ShippingInfo(Money.ZERO, null, null, null);

    public ShippingInfo withCost(Money cost) {
        return new ShippingInfo(cost, shippingMethod, carrier, trackingNumber);
    }

    public ShippingInfo withTracking(String carrier, String trackingNumber) {
        return new ShippingInfo(shippingCost, shippingMethod, carrier, trackingNumber);
    }
}
