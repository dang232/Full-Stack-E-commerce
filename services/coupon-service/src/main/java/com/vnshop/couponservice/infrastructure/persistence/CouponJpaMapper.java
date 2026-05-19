package com.vnshop.couponservice.infrastructure.persistence;

import com.vnshop.couponservice.domain.Coupon;
import com.vnshop.couponservice.domain.DiscountType;

/**
 * Bidirectional mapping between the {@link Coupon} aggregate and its JPA row.
 * Lives next to the JPA entity so changes to the column shape only ripple to
 * one file.
 */
final class CouponJpaMapper {
    private CouponJpaMapper() {}

    static Coupon toDomain(CouponJpaEntity row) {
        return new Coupon(
                row.getId(),
                row.getCode(),
                DiscountType.fromWire(row.getDiscountType()),
                row.getDiscountValue(),
                row.getMinOrderValue(),
                row.getMaxDiscount(),
                row.getMaxUses(),
                row.getCurrentUses(),
                row.isActive(),
                row.getValidFrom(),
                row.getValidUntil());
    }

    static CouponJpaEntity toRow(Coupon coupon, CouponJpaEntity existing) {
        CouponJpaEntity row = (existing != null) ? existing : new CouponJpaEntity();
        row.setCode(coupon.code());
        row.setDiscountType(coupon.discountType().name());
        row.setDiscountValue(coupon.discountValue());
        row.setMinOrderValue(coupon.minOrderValue());
        row.setMaxDiscount(coupon.maxDiscount());
        row.setMaxUses(coupon.maxUses());
        // currentUses is owned by the DB on the apply path (atomic UPDATE), so we
        // only initialise it on insert. For updates we leave the existing column
        // value alone — see the load-or-new branch above.
        if (existing == null) {
            row.setCurrentUses(coupon.currentUses());
            row.setActive(coupon.active());
            row.setValidFrom(coupon.validFrom());
        } else {
            row.setActive(coupon.active());
        }
        row.setValidUntil(coupon.validUntil());
        return row;
    }
}
