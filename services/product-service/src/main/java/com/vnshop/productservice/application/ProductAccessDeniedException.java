package com.vnshop.productservice.application;

/**
 * Pt19 audit fix: thrown when a JWT-authenticated caller attempts to act on a
 * product (or product-scoped resource like an image objectKey) they do not own.
 *
 * <p>The {@link com.vnshop.productservice.infrastructure.web.ApiExceptionHandler}
 * maps this to HTTP 403 — distinct from {@code IllegalArgumentException} (400)
 * to avoid leaking the difference between "doesn't exist" and "exists but not
 * yours" while still surfacing an actionable status to legitimate callers.
 */
public class ProductAccessDeniedException extends RuntimeException {
    public ProductAccessDeniedException(String message) {
        super(message);
    }
}
