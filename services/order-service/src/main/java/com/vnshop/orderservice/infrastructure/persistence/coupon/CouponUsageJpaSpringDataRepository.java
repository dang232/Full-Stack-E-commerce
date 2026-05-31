package com.vnshop.orderservice.infrastructure.persistence.coupon;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageJpaSpringDataRepository extends JpaRepository<CouponUsageJpaEntity, UUID> {
    int countByCouponIdAndUserIdAndActiveTrue(UUID couponId, String userId);

    Optional<CouponUsageJpaEntity> findFirstByCouponIdAndUserIdAndActiveTrue(UUID couponId, String userId);

    Optional<CouponUsageJpaEntity> findFirstByOrderIdAndUserIdAndActiveTrue(UUID orderId, String userId);
}
