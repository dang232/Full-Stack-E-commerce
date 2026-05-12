package com.vnshop.orderservice.application.coupon;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponException;
import com.vnshop.orderservice.domain.coupon.CouponRepository;
import com.vnshop.orderservice.domain.coupon.CouponUsageRepository;
import com.vnshop.orderservice.domain.coupon.CouponValidator;
import org.springframework.transaction.annotation.Transactional;

public class ApplyCouponUseCase {
    private final CouponValidator couponValidator;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;

    public ApplyCouponUseCase(CouponValidator couponValidator, CouponRepository couponRepository, CouponUsageRepository usageRepository) {
        this.couponValidator = couponValidator;
        this.couponRepository = couponRepository;
        this.usageRepository = usageRepository;
    }

    @Transactional
    public ApplyCouponResponse apply(ApplyCouponCommand command) {
        CouponValidator.ValidationResult result = couponValidator.validate(command.code(), new Money(command.orderTotal()), command.userId());
        if (!result.valid()) {
            throw new CouponException(result.errorCode(), result.reason());
        }
        Coupon coupon = result.coupon();
        coupon.recordUsage();
        Coupon saved = couponRepository.save(coupon);
        usageRepository.recordUsage(coupon.id(), command.userId(), command.orderId());
        Money newTotal = new Money(command.orderTotal()).subtract(result.discount());
        return new ApplyCouponResponse(newTotal, result.discount(), CouponMapper.toResponse(saved));
    }
}
