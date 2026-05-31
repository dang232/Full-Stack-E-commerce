package com.vnshop.orderservice.infrastructure.product;

/**
 * Thrown when the product-service can't be reached or returned an error
 * other than 404. Mapped to 503 by {@code ApiExceptionHandler} so checkout
 * fails loud rather than letting an order go through with stale or missing
 * pricing.
 */
public class ProductCatalogUnavailableException extends RuntimeException {
    public ProductCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
