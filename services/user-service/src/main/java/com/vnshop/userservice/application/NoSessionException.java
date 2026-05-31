package com.vnshop.userservice.application;

public class NoSessionException extends RuntimeException {
    public NoSessionException(String message) {
        super(message);
    }
}
