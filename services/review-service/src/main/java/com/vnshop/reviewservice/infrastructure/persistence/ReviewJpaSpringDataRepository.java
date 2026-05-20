package com.vnshop.reviewservice.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReviewJpaSpringDataRepository extends JpaRepository<ReviewJpaEntity, UUID> {
    List<ReviewJpaEntity> findByProductId(String productId);

    List<ReviewJpaEntity> findByBuyerId(String buyerId);
}
