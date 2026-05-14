package com.vnshop.couponservice.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CouponController {
    private static final String DISCOUNT_TYPE_FIXED = "FIXED";
    private static final String DISCOUNT_TYPE_PERCENTAGE = "PERCENTAGE";

    private final CouponRepository couponRepository;

    public CouponController(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @PostMapping({"/coupons", "/admin/coupons"})
    @ResponseStatus(HttpStatus.CREATED)
    public CouponJpaEntity createCoupon(@RequestBody CreateCouponRequest request) {
        CouponJpaEntity coupon = new CouponJpaEntity();
        applyRequest(coupon, request);
        coupon.setCurrentUses(0);
        coupon.setActive(true);
        coupon.setValidFrom(Instant.now());
        return couponRepository.save(coupon);
    }

    @GetMapping("/coupons")
    public List<CouponJpaEntity> listActiveCoupons() {
        return couponRepository.findByActiveTrue();
    }

    @GetMapping("/admin/coupons")
    public List<CouponJpaEntity> listCoupons() {
        return couponRepository.findAll();
    }

    @PutMapping("/admin/coupons/{id}")
    public CouponJpaEntity updateCoupon(@PathVariable Long id, @RequestBody CreateCouponRequest request) {
        CouponJpaEntity coupon = findCoupon(id);
        applyRequest(coupon, request);
        return couponRepository.save(coupon);
    }

    @PostMapping("/admin/coupons/{id}/deactivate")
    public CouponJpaEntity deactivateCoupon(@PathVariable Long id) {
        CouponJpaEntity coupon = findCoupon(id);
        coupon.setActive(false);
        return couponRepository.save(coupon);
    }

    @PostMapping({"/coupons/validate", "/checkout/validate-coupon"})
    public ValidateCouponResponse validateCoupon(@RequestBody ValidateCouponRequest request) {
        BigDecimal orderAmount = request.orderAmount() != null ? request.orderAmount() : request.orderTotal();
        return couponRepository.findByCode(request.code())
                .map(coupon -> validateExistingCoupon(coupon, orderAmount))
                .orElseGet(() -> new ValidateCouponResponse(false, BigDecimal.ZERO, "Coupon not found"));
    }

    @PostMapping("/checkout/apply-coupon")
    public ApplyCouponResponse applyCoupon(@RequestBody ApplyCouponRequest request) {
        BigDecimal orderAmount = request.orderAmount() != null ? request.orderAmount() : request.orderTotal();
        CouponJpaEntity coupon = couponRepository.findByCode(request.code())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
        ValidateCouponResponse validation = validateExistingCoupon(coupon, orderAmount);
        if (!validation.valid()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, validation.message());
        }
        coupon.setCurrentUses(coupon.getCurrentUses() + 1);
        couponRepository.save(coupon);
        return new ApplyCouponResponse(coupon.getCode(), validation.discount(), orderAmount.subtract(validation.discount()));
    }

    private CouponJpaEntity findCoupon(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
    }

    private void applyRequest(CouponJpaEntity coupon, CreateCouponRequest request) {
        coupon.setCode(request.code());
        coupon.setDiscountType(request.discountType() != null ? request.discountType() : request.type());
        coupon.setDiscountValue(request.discountValue() != null ? request.discountValue() : request.value());
        coupon.setMinOrderValue(request.minOrderValue() != null ? request.minOrderValue() : BigDecimal.ZERO);
        coupon.setMaxDiscount(request.maxDiscount());
        coupon.setMaxUses(request.maxUses() > 0 ? request.maxUses() : request.totalUsageLimit());
        coupon.setValidUntil(request.validUntil());
    }

    private ValidateCouponResponse validateExistingCoupon(CouponJpaEntity coupon, BigDecimal orderAmount) {
        if (!coupon.isActive()) {
            return new ValidateCouponResponse(false, BigDecimal.ZERO, "Coupon is inactive");
        }
        if (coupon.getValidUntil().isBefore(Instant.now())) {
            return new ValidateCouponResponse(false, BigDecimal.ZERO, "Coupon is expired");
        }
        if (orderAmount.compareTo(coupon.getMinOrderValue()) < 0) {
            return new ValidateCouponResponse(false, BigDecimal.ZERO, "Order amount is below minimum");
        }
        if (coupon.getCurrentUses() >= coupon.getMaxUses()) {
            return new ValidateCouponResponse(false, BigDecimal.ZERO, "Coupon usage limit exceeded");
        }

        BigDecimal discount = calculateDiscount(coupon, orderAmount);
        return new ValidateCouponResponse(true, discount, "Coupon is valid");
    }

    private BigDecimal calculateDiscount(CouponJpaEntity coupon, BigDecimal orderAmount) {
        BigDecimal discount;
        if (DISCOUNT_TYPE_PERCENTAGE.equalsIgnoreCase(coupon.getDiscountType())) {
            discount = orderAmount.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100));
        } else if (DISCOUNT_TYPE_FIXED.equalsIgnoreCase(coupon.getDiscountType())) {
            discount = coupon.getDiscountValue();
        } else {
            discount = BigDecimal.ZERO;
        }

        if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
            return coupon.getMaxDiscount();
        }
        return discount;
    }

    public record CreateCouponRequest(
            String code,
            String discountType,
            String type,
            BigDecimal discountValue,
            BigDecimal value,
            BigDecimal minOrderValue,
            BigDecimal maxDiscount,
            int maxUses,
            int totalUsageLimit,
            int perUserLimit,
            Instant validUntil) {
    }

    public record ValidateCouponRequest(String code, BigDecimal orderAmount, BigDecimal orderTotal, String userId) {
    }

    public record ValidateCouponResponse(boolean valid, BigDecimal discount, String message) {
    }

    public record ApplyCouponRequest(String code, Long orderId, String userId, BigDecimal orderAmount, BigDecimal orderTotal) {
    }

    public record ApplyCouponResponse(String code, BigDecimal discount, BigDecimal finalTotal) {
    }
}
