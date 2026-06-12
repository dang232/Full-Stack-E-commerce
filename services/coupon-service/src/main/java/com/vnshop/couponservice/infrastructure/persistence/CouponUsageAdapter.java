package com.vnshop.couponservice.infrastructure.persistence;

import com.vnshop.couponservice.domain.port.out.CouponUsagePort;
import org.springframework.stereotype.Component;

@Component
public class CouponUsageAdapter implements CouponUsagePort {

    private final CouponUsageRepository repository;

    public CouponUsageAdapter(CouponUsageRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean hasUserUsedCoupon(Long couponId, String userId) {
        return repository.existsByCouponIdAndUserId(couponId, userId);
    }

    @Override
    public void recordUsage(Long couponId, String userId) {
        repository.save(new CouponUsageJpaEntity(couponId, userId));
    }
}
