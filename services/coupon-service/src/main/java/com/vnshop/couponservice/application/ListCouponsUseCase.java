package com.vnshop.couponservice.application;

import com.vnshop.couponservice.domain.Coupon;
import com.vnshop.couponservice.domain.port.out.CouponRepository;
import java.util.List;
import java.util.Objects;

/**
 * Read-only listing for FE buyer (active only) and admin (all) screens.
 */
public class ListCouponsUseCase {
    private final CouponRepository repository;

    public ListCouponsUseCase(CouponRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<Coupon> active() {
        return repository.findActive();
    }

    public List<Coupon> all() {
        return repository.findAll();
    }
}
