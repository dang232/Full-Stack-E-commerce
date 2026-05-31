package com.vnshop.paymentservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentIdempotencyKeySpringDataRepository extends JpaRepository<PaymentIdempotencyKeyJpaEntity, String> {
}
