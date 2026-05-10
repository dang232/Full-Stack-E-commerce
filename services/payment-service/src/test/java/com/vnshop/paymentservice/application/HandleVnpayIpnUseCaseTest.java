package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HandleVnpayIpnUseCaseTest {
    @Test
    void updatesPendingVnpayPaymentAfterVerifiedIpnOnly() {
        CapturingPaymentRepository repository = new CapturingPaymentRepository(payment(PaymentStatus.PENDING, null));
        HandleVnpayIpnUseCase useCase = new HandleVnpayIpnUseCase(repository);

        Payment updated = useCase.applyVerifiedResult("PAY-1", PaymentStatus.COMPLETED, "14123456");

        assertThat(updated.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(updated.transactionRef()).isEqualTo("14123456");
        assertThat(repository.savedPayments).hasSize(1);
    }

    @Test
    void keepsAlreadyResolvedPaymentIdempotent() {
        CapturingPaymentRepository repository = new CapturingPaymentRepository(payment(PaymentStatus.COMPLETED, "14123456"));
        HandleVnpayIpnUseCase useCase = new HandleVnpayIpnUseCase(repository);

        Payment existing = useCase.applyVerifiedResult("PAY-1", PaymentStatus.COMPLETED, "14123456");

        assertThat(existing.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(repository.savedPayments).isEmpty();
    }

    private static Payment payment(PaymentStatus status, String transactionRef) {
        return new Payment("PAY-1", "ORDER-1", "BUYER-1", new BigDecimal("120000.00"), Payment.Method.VNPAY, status, transactionRef, Instant.parse("2026-05-10T09:00:00Z"));
    }

    private static final class CapturingPaymentRepository implements PaymentRepositoryPort {
        private Payment payment;
        private final List<Payment> savedPayments = new ArrayList<>();

        private CapturingPaymentRepository(Payment payment) {
            this.payment = payment;
        }

        @Override
        public Payment save(Payment payment) {
            this.payment = payment;
            savedPayments.add(payment);
            return payment;
        }

        @Override
        public Optional<Payment> findById(String paymentId) {
            return payment.paymentId().equals(paymentId) ? Optional.of(payment) : Optional.empty();
        }

        @Override
        public Optional<Payment> findByOrderId(String orderId) {
            return Optional.empty();
        }

        @Override
        public List<Payment> findByStatus(PaymentStatus status) {
            return List.of();
        }
    }
}
