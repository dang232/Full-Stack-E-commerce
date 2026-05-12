package com.vnshop.reviewservice.infrastructure.persistence;

import com.vnshop.reviewservice.domain.ReviewStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReviewJpaSpringDataRepository extends JpaRepository<ReviewJpaEntity, UUID> {
    List<ReviewJpaEntity> findByProductId(String productId);

    List<ReviewJpaEntity> findByBuyerId(String buyerId);

    List<ReviewJpaEntity> findByStatus(ReviewStatus status);
}
