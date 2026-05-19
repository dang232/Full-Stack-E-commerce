package com.vnshop.couponservice.application;

import com.vnshop.couponservice.domain.Coupon;
import com.vnshop.couponservice.domain.port.out.CouponRepository;
import java.util.Objects;

/**
 * Disable a coupon so future {@link com.vnshop.couponservice.domain.Coupon#validate}
 * calls reject it. The aggregate's {@code currentUses} is preserved — this is
 * a soft toggle, not a deletion.
 */
public class DeactivateCouponUseCase {
    private final CouponRepository repository;

    public DeactivateCouponUseCase(CouponRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Coupon deactivate(Long couponId) {
        Coupon coupon = repository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException("Coupon not found"));
        coupon.deactivate();
        return repository.save(coupon);
    }
}
