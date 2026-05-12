package com.vnshop.orderservice.infrastructure.persistence.coupon;

import com.vnshop.orderservice.domain.coupon.CouponId;
import com.vnshop.orderservice.domain.coupon.CouponUsageRepository;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CouponUsageJpaRepository implements CouponUsageRepository {
    private final CouponUsageJpaSpringDataRepository springDataRepository;

    public CouponUsageJpaRepository(CouponUsageJpaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public int getUsageCount(CouponId couponId, String userId) {
        return springDataRepository.countByCouponIdAndUserIdAndActiveTrue(couponId.value(), userId);
    }

    @Override
    public void recordUsage(CouponId couponId, String userId) {
        recordUsage(couponId, userId, UUID.randomUUID());
    }

    @Override
    public void recordUsage(CouponId couponId, String userId, UUID orderId) {
        CouponUsageJpaEntity entity = new CouponUsageJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setCouponId(couponId.value());
        entity.setUserId(userId);
        entity.setOrderId(orderId);
        entity.setActive(true);
        springDataRepository.save(entity);
    }

    @Override
    public void releaseUsage(CouponId couponId, String userId) {
        springDataRepository.findFirstByCouponIdAndUserIdAndActiveTrue(couponId.value(), userId).ifPresent(entity -> {
            entity.setActive(false);
            springDataRepository.save(entity);
        });
    }

    @Override
    public java.util.Optional<CouponId> releaseUsageForOrder(UUID orderId, String userId) {
        return springDataRepository.findFirstByOrderIdAndUserIdAndActiveTrue(orderId, userId).map(entity -> {
            entity.setActive(false);
            springDataRepository.save(entity);
            return new CouponId(entity.getCouponId());
        });
    }
}
