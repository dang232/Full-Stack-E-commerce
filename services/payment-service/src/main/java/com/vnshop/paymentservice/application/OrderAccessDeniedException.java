package com.vnshop.paymentservice.application;

/**
 * Thrown when the authenticated principal doesn't own the order they're
 * trying to pay for. Mapped to 403. Covers the cross-buyer-tampering
 * variant of the pt12 finding.
 */
public class OrderAccessDeniedException extends RuntimeException {
    public OrderAccessDeniedException(String message) {
        super(message);
    }
}
