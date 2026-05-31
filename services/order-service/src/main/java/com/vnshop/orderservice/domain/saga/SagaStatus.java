package com.vnshop.orderservice.domain.saga;

public enum SagaStatus {
    STARTED,
    INVENTORY_RESERVED,
    PAYMENT_CHARGED,
    SHIPPING_CREATED,
    COMPLETED,
    COMPENSATING,
    FAILED
}