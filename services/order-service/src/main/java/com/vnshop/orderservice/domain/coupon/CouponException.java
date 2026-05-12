package com.vnshop.orderservice.domain.coupon;

public class CouponException extends RuntimeException {
    private final String code;

    public CouponException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
