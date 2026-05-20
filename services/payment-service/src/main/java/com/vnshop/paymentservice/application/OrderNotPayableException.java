package com.vnshop.paymentservice.application;

/**
 * Thrown when the order is not in a payable state (already paid, cancelled,
 * etc.). Mapped to 409 Conflict.
 */
public class OrderNotPayableException extends RuntimeException {
    public OrderNotPayableException(String message) {
        super(message);
    }
}
