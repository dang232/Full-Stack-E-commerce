package com.vnshop.shippingservice.infrastructure.web;

public class TrackingNotFoundException extends RuntimeException {
    public TrackingNotFoundException(String message) {
        super(message);
    }
}
