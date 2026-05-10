package com.vnshop.paymentservice.infrastructure.gateway;

public interface PaymentCallbackOutbox {
    PaymentCallbackOutboxRecord save(PaymentCallbackOutboxRecord record);
}
