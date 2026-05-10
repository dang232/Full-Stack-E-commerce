package com.vnshop.orderservice.domain;

public class InvoiceAccessDeniedException extends RuntimeException {
    public InvoiceAccessDeniedException(String message) {
        super(message);
    }
}
