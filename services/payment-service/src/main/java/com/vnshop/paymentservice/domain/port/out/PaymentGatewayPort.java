package com.vnshop.paymentservice.domain.port.out;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;

public interface PaymentGatewayPort {
    GatewayPaymentResult processPayment(Payment payment);

    PaymentStatus getStatus(String paymentId);

    record GatewayPaymentResult(PaymentStatus status, String transactionRef) {
    }
}
