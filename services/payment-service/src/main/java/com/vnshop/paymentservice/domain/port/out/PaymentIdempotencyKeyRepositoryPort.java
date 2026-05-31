package com.vnshop.paymentservice.domain.port.out;

import com.vnshop.paymentservice.domain.PaymentIdempotencyKey;

import java.util.Optional;

public interface PaymentIdempotencyKeyRepositoryPort {
    Optional<PaymentIdempotencyKey> findByKey(String key);

    PaymentIdempotencyKey save(PaymentIdempotencyKey key);
}
