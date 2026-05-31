package com.vnshop.couponservice.application;

import com.vnshop.couponservice.domain.Coupon;
import com.vnshop.couponservice.domain.DiscountType;
import com.vnshop.couponservice.domain.port.out.CouponRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Issues a new coupon. Only the input mapping plus the call to the aggregate
 * factory; all invariant enforcement (positive maxUses, percentage 0–100,
 * future validUntil, etc.) lives on {@link Coupon#issue}.
 */
public class IssueCouponUseCase {
    private final CouponRepository repository;
    private final Clock clock;

    public IssueCouponUseCase(CouponRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Coupon issue(CouponTermsCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            Coupon coupon = Coupon.issue(
                    command.code(),
                    DiscountType.fromWire(command.discountType()),
                    command.discountValue(),
                    command.minOrderValue(),
                    command.maxDiscount(),
                    command.maxUses(),
                    command.validUntil(),
                    clock);
            return repository.save(coupon);
        } catch (IllegalArgumentException e) {
            throw new CouponDomainException(e.getMessage());
        }
    }
}
