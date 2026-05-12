package com.vnshop.orderservice.application.coupon;

import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponId;
import com.vnshop.orderservice.domain.coupon.CouponRepository;
import com.vnshop.orderservice.domain.coupon.CouponUsageRepository;
import org.springframework.transaction.annotation.Transactional;

public class ReleaseCouponUsageUseCase {
    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;

    public ReleaseCouponUsageUseCase(CouponRepository couponRepository, CouponUsageRepository usageRepository) {
        this.couponRepository = couponRepository;
        this.usageRepository = usageRepository;
    }

    @Transactional
    public void release(CouponId couponId, String userId) {
        Coupon coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null) {
            return;
        }
        coupon.releaseUsage();
        couponRepository.save(coupon);
        usageRepository.releaseUsage(couponId, userId);
    }

    @Transactional
    public void releaseForOrder(java.util.UUID orderId, String userId) {
        usageRepository.releaseUsageForOrder(orderId, userId).ifPresent(couponId -> {
            Coupon coupon = couponRepository.findById(couponId).orElse(null);
            if (coupon != null) {
                coupon.releaseUsage();
                couponRepository.save(coupon);
            }
        });
    }
}
