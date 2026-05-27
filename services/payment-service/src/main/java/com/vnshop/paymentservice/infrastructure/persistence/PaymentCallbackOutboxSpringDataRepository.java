package com.vnshop.paymentservice.infrastructure.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentCallbackOutboxSpringDataRepository extends JpaRepository<PaymentCallbackOutboxJpaEntity, Long> {
    List<PaymentCallbackOutboxJpaEntity> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
