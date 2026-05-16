package com.vnshop.paymentservice.application;

/**
 * Thrown when an Idempotency-Key is reused with a request body that hashes
 * differently from the body the key was originally registered with.
 *
 * <p>This is a client error mapped to HTTP 422 by the API exception handler.
 */
public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
