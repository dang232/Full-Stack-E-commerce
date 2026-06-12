package com.vnshop.couponservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsageJpaEntity, Long> {

    boolean existsByCouponIdAndUserId(Long couponId, String userId);
}
