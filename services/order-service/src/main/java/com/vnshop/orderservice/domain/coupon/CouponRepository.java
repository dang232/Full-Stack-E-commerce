package com.vnshop.orderservice.domain.coupon;

import java.util.Optional;

public interface CouponRepository {
    Optional<Coupon> findByCode(String code);
}
