package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;

import java.util.Objects;

public class GetPaymentStatusUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;

    public GetPaymentStatusUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = Objects.requireNonNull(paymentRepositoryPort, "paymentRepositoryPort is required");
    }

    /**
     * Service-to-service / unauthenticated read. The gRPC server uses this
     * because callers there are trusted (order-service polling for status).
     * Don't expose this directly from any HTTP controller — use
     * {@link #getByOrderIdForBuyer(String, String)} instead so the JWT
     * principal is verified against the payment's buyer.
     */
    public Payment getByOrderId(String orderId) {
        return paymentRepositoryPort.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("payment not found for order: " + orderId));
    }

    /**
     * HTTP read. Verifies the authenticated buyer owns the underlying payment
     * before returning it. Closes an IDOR where any authenticated buyer could
     * read any other buyer's payment status by guessing the orderId UUID.
     */
    public Payment getByOrderIdForBuyer(String orderId, String buyerId) {
        Payment payment = getByOrderId(orderId);
        if (!payment.buyerId().equals(buyerId)) {
            throw new OrderAccessDeniedException(
                    "buyer " + buyerId + " does not own payment for order " + orderId);
        }
        return payment;
    }
}
