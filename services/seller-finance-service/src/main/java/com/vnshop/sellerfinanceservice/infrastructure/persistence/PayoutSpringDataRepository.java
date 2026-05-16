package com.vnshop.sellerfinanceservice.infrastructure.persistence;

import com.vnshop.sellerfinanceservice.domain.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayoutSpringDataRepository extends JpaRepository<PayoutJpaEntity, UUID> {
    List<PayoutJpaEntity> findByStatus(PayoutStatus status);

    List<PayoutJpaEntity> findBySellerId(String sellerId);
}
