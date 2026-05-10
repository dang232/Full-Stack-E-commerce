package com.vnshop.paymentservice.infrastructure.gateway;

import java.util.Optional;

public interface PaymentCallbackLogStore {
    Optional<PaymentCallbackAttempt> findProcessed(String provider, String eventId, String payloadHash, String signatureHash);

    PaymentCallbackAttempt save(PaymentCallbackAttempt attempt);
}
