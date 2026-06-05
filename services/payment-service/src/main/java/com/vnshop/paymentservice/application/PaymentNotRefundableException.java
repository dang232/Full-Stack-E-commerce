package com.vnshop.paymentservice.application;

public class PaymentNotRefundableException extends RuntimeException {
    public PaymentNotRefundableException(String message) {
        super(message);
    }
}
