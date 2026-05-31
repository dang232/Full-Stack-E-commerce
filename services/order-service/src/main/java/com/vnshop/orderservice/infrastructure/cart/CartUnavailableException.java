package com.vnshop.orderservice.infrastructure.cart;

public class CartUnavailableException extends RuntimeException {
    public CartUnavailableException(String message) {
        super(message);
    }

    public CartUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
