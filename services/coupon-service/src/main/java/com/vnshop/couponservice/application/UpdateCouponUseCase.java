package com.vnshop.couponservice.application;

import com.vnshop.couponservice.domain.Coupon;
import com.vnshop.couponservice.domain.DiscountType;
import com.vnshop.couponservice.domain.port.out.CouponRepository;
import java.util.Objects;

/**
 * Replace an existing coupon's editable terms. Identity stays the same; the
 * aggregate validates the new field values before the repository persists.
 */
public class UpdateCouponUseCase {
    private final CouponRepository repository;

    public UpdateCouponUseCase(CouponRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Coupon update(Long couponId, CouponTermsCommand command) {
        Objects.requireNonNull(command, "command");
        Coupon coupon = repository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException("Coupon not found"));
        try {
            coupon.editTerms(
                    command.code(),
                    DiscountType.fromWire(command.discountType()),
                    command.discountValue(),
                    command.minOrderValue(),
                    command.maxDiscount(),
                    command.maxUses(),
                    command.validUntil());
        } catch (IllegalArgumentException e) {
            throw new CouponDomainException(e.getMessage());
        }
        return repository.save(coupon);
    }
}
