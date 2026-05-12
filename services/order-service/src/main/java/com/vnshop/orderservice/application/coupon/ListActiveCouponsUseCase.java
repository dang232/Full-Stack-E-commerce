package com.vnshop.orderservice.application.coupon;

import com.vnshop.orderservice.domain.coupon.CouponRepository;
import java.util.List;

public class ListActiveCouponsUseCase {
    private final CouponRepository couponRepository;

    public ListActiveCouponsUseCase(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    public List<CouponResponse> listActive() {
        return couponRepository.findActive().stream().map(CouponMapper::toResponse).toList();
    }

    public List<CouponResponse> listAll() {
        return couponRepository.findAll().stream().map(CouponMapper::toResponse).toList();
    }
}
