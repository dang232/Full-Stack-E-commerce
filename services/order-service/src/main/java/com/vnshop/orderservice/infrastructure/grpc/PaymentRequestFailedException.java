package com.vnshop.orderservice.infrastructure.grpc;

/**
 * Thrown when a gRPC payment request to the payment service fails.
 */
public class PaymentRequestFailedException extends RuntimeException {

    public PaymentRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
