package com.vnshop.userservice.application;

public class RefreshTokenRejectedException extends RuntimeException {
    public RefreshTokenRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
