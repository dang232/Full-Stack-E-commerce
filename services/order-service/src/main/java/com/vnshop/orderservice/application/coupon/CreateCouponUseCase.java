package com.vnshop.orderservice.application.coupon;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponException;
import com.vnshop.orderservice.domain.coupon.CouponId;
import com.vnshop.orderservice.domain.coupon.CouponRepository;
import com.vnshop.orderservice.domain.coupon.DiscountType;
import java.math.BigDecimal;

public class CreateCouponUseCase {
    private final CouponRepository couponRepository;

    public CreateCouponUseCase(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    public CouponResponse create(CreateCouponCommand command) {
        String code = Coupon.normalizeCode(command.code());
        if (couponRepository.existsByCode(code)) {
            throw new CouponException("COUPON_CODE_DUPLICATE", "coupon code already exists");
        }
        Coupon coupon = Coupon.create(
                CouponId.generate(),
                code,
                DiscountType.valueOf(command.type()),
                command.value(),
                moneyOrNull(command.maxDiscount()),
                moneyOrZero(command.minOrderValue()),
                command.totalUsageLimit(),
                command.perUserLimit(),
                command.validFrom(),
                command.validUntil()
        );
        return CouponMapper.toResponse(couponRepository.save(coupon));
    }

    private static Money moneyOrNull(BigDecimal amount) {
        return amount == null ? null : new Money(amount);
    }

    private static Money moneyOrZero(BigDecimal amount) {
        return amount == null ? Money.ZERO : new Money(amount);
    }
}
