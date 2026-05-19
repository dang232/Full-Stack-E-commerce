package com.vnshop.couponservice.domain.port.out;

import com.vnshop.couponservice.domain.Coupon;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port the application layer uses to load and persist coupons.
 * Concrete implementation lives in {@code infrastructure.persistence}; the
 * domain remains free of JPA / Spring imports.
 */
public interface CouponRepository {
    Coupon save(Coupon coupon);

    Optional<Coupon> findById(Long id);

    Optional<Coupon> findByCode(String code);

    List<Coupon> findAll();

    List<Coupon> findActive();

    /**
     * Atomically increments {@code currentUses} only if there is remaining
     * capacity. Returns {@code true} when the caller won the seat, {@code false}
     * when the coupon was already exhausted (i.e. another concurrent apply
     * consumed the last slot). Implementations must perform the predicate
     * evaluation and the increment in a single statement so the DB serialises
     * concurrent applies.
     */
    boolean tryConsumeUsage(Long couponId);
}
