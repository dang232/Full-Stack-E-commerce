package com.vnshop.paymentservice.infrastructure.order;

/**
 * Thrown when order-service can't be reached or returned a non-404 error.
 * Mapped to 503 by {@code ApiExceptionHandler} so a checkout fails loud
 * rather than letting a payment go through against a stale or missing
 * order snapshot.
 */
public class OrderCatalogUnavailableException extends RuntimeException {
    public OrderCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
