package com.vnshop.paymentservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentCallbackOutboxSpringDataRepository extends JpaRepository<PaymentCallbackOutboxJpaEntity, Long> {
}
