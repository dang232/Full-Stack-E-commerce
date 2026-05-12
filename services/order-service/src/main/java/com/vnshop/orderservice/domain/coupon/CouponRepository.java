package com.vnshop.orderservice.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    Optional<Coupon> findById(CouponId id);

    Optional<Coupon> findByCode(String code);

    Coupon save(Coupon coupon);

    List<Coupon> findActive();

    List<Coupon> findAll();

    boolean existsByCode(String code);
}
