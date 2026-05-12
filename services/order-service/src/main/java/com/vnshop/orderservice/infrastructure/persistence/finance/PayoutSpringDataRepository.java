package com.vnshop.orderservice.infrastructure.persistence.finance;

import com.vnshop.orderservice.domain.finance.PayoutStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PayoutSpringDataRepository extends JpaRepository<PayoutJpaEntity, UUID> {
    List<PayoutJpaEntity> findByStatus(PayoutStatus status);

    List<PayoutJpaEntity> findBySellerId(String sellerId);
}
