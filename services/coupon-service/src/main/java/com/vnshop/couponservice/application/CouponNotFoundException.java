package com.vnshop.couponservice.application;

/**
 * Application-layer signal that a referenced coupon does not exist. The web
 * layer maps this onto {@code NOT_FOUND}.
 */
public class CouponNotFoundException extends RuntimeException {
    public CouponNotFoundException(String message) {
        super(message);
    }
}
