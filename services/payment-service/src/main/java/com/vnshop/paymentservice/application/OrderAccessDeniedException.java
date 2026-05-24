package com.vnshop.paymentservice.application;

/**
 * Thrown when a caller doesn't own the payment they're trying to act on.
 * Mapped to 403 by {@code ApiExceptionHandler}. Messages are intentionally
 * generic — never echo the requested paymentId, orderId, or callerId back,
 * since that turns the response into an authorization-test oracle (gotcha
 * #102 in the auto-memory).
 */
public class OrderAccessDeniedException extends RuntimeException {
    public OrderAccessDeniedException(String message) {
        super(message);
    }
}
