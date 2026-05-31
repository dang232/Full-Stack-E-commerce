package com.vnshop.paymentservice.application;

/**
 * Thrown when an order referenced by a payment create endpoint doesn't
 * exist in order-service. Mapped to 404.
 */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("order not found: " + orderId);
    }
}
