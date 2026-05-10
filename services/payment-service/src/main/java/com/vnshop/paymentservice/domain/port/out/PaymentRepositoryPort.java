package com.vnshop.paymentservice.domain.port.out;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;

import java.util.List;
import java.util.Optional;

public interface PaymentRepositoryPort {
    Payment save(Payment payment);

    Optional<Payment> findById(String paymentId);

    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findByStatus(PaymentStatus status);
}
