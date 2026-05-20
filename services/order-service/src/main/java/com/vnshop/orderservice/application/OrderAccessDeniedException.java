package com.vnshop.orderservice.application;

/**
 * Thrown when a buyer attempts to access an order they don't own. Mapped
 * to 403 by {@code ApiExceptionHandler}.
 */
public class OrderAccessDeniedException extends RuntimeException {
    public OrderAccessDeniedException(String message) {
        super(message);
    }
}
