package com.vnshop.paymentservice.infrastructure.persistence;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentJpaRepository implements PaymentRepositoryPort {
    private final PaymentJpaSpringDataRepository springDataRepository;

    public PaymentJpaRepository(PaymentJpaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Payment save(Payment payment) {
        return springDataRepository.save(PaymentJpaEntity.fromDomain(payment)).toDomain();
    }

    @Override
    public Optional<Payment> findById(UUID paymentId) {
        return springDataRepository.findById(paymentId).map(PaymentJpaEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return springDataRepository.findByOrderId(orderId).map(PaymentJpaEntity::toDomain);
    }

    @Override
    public List<Payment> findByStatus(PaymentStatus status) {
        return springDataRepository.findByStatus(status).stream()
                .map(PaymentJpaEntity::toDomain)
                .toList();
    }
}
