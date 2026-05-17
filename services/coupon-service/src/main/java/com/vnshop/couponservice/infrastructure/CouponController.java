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
    static final String CODE_COUPON_EXHAUSTED = "COUPON_EXHAUSTED";

    private final CouponRepository couponRepository;

    public CouponController(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @PostMapping({"/coupons", "/admin/coupons"})
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse createCoupon(@RequestBody CreateCouponRequest request) {
        CouponJpaEntity coupon = new CouponJpaEntity();
        applyRequest(coupon, request);
        coupon.setCurrentUses(0);
        coupon.setActive(true);
        coupon.setValidFrom(Instant.now());
        return CouponResponse.from(couponRepository.save(coupon));
    }

    @GetMapping("/coupons")
    public List<CouponResponse> listActiveCoupons() {
        return couponRepository.findByActiveTrue().stream().map(CouponResponse::from).toList();
    }

    @GetMapping("/admin/coupons")
    public List<CouponResponse> listCoupons() {
        return couponRepository.findAll().stream().map(CouponResponse::from).toList();
    }

    @PutMapping("/admin/coupons/{id}")
    public CouponResponse updateCoupon(@PathVariable Long id, @RequestBody CreateCouponRequest request) {
        CouponJpaEntity coupon = findCoupon(id);
        applyRequest(coupon, request);
        return CouponResponse.from(couponRepository.save(coupon));
    }

    @PostMapping("/admin/coupons/{id}/deactivate")
    public CouponResponse deactivateCoupon(@PathVariable Long id) {
        CouponJpaEntity coupon = findCoupon(id);
        coupon.setActive(false);
        return CouponResponse.from(couponRepository.save(coupon));
    }

    @PostMapping({"/coupons/validate", "/checkout/validate-coupon"})
    public ValidateCouponResponse validateCoupon(@RequestBody ValidateCouponRequest request) {
        BigDecimal orderAmount = request.orderAmount() != null ? request.orderAmount() : request.orderTotal();
        return couponRepository.findByCode(request.code())
                .map(coupon -> validateExistingCoupon(coupon, orderAmount))
                .orElseGet(() -> new ValidateCouponResponse(false, BigDecimal.ZERO, "Coupon not found"));
    }

    /**
     * Issue 2 fix: the previous flow read currentUses, validated, then incremented + saved
     * in three separate statements. Two concurrent requests on the last seat both observed
     * room and both proceeded to consume — overselling. We now do an atomic conditional
     * increment ({@link CouponRepository#tryConsumeUsage(Long)}); the DB enforces the
     * invariant {@code currentUses &lt; maxUses} inside the UPDATE itself, so exactly one
     * concurrent caller wins. Other validation (active, expiry, min order) still runs
     * before the consume attempt so we can return a useful error message — the consume
     * step is the only one that races.
     */
    @PostMapping("/checkout/apply-coupon")
    public ApplyCouponResponse applyCoupon(@RequestBody ApplyCouponRequest request) {
        BigDecimal orderAmount = request.orderAmount() != null ? request.orderAmount() : request.orderTotal();
        CouponJpaEntity coupon = couponRepository.findByCode(request.code())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));

        // Pre-flight checks that don't race (or for which a stale read still produces the
        // correct outcome on retry): expiry / inactive / min-order. We surface those as 422
        // with descriptive messages.
        if (!coupon.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Coupon is inactive");
        }
        if (coupon.getValidUntil().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Coupon is expired");
        }
        if (orderAmount.compareTo(coupon.getMinOrderValue()) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Order amount is below minimum");
        }

        // Atomic consume-or-fail. The conditional UPDATE is the synchronization point —
        // returns 1 on success, 0 if the coupon was just exhausted by a concurrent caller.
        int updated = couponRepository.tryConsumeUsage(coupon.getId());
        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    CODE_COUPON_EXHAUSTED + ": Coupon usage limit exceeded"
            );
        }

        BigDecimal discount = calculateDiscount(coupon, orderAmount);
        if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
            discount = coupon.getMaxDiscount();
        }
        return new ApplyCouponResponse(coupon.getCode(), discount, orderAmount.subtract(discount));
    }

    private CouponJpaEntity findCoupon(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
    }

    private void applyRequest(CouponJpaEntity coupon, CreateCouponRequest request) {
        coupon.setCode(request.code());
        // Wire-format names (`type`/`value`/`maxUses`) map onto entity fields
        // (`discountType`/`discountValue`/`maxUses`). The wire names match what FE
        // admin and buyer code already sends and reads; the entity names match the
        // existing column names.
        coupon.setDiscountType(request.type());
        coupon.setDiscountValue(request.value());
        coupon.setMinOrderValue(request.minOrderValue() != null ? request.minOrderValue() : BigDecimal.ZERO);
        coupon.setMaxDiscount(request.maxDiscount());
        coupon.setMaxUses(request.maxUses());
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

    /**
     * Canonical create/update payload. The previous shape carried alias pairs
     * ({@code discountType}/{@code type}, {@code discountValue}/{@code value},
     * {@code maxUses}/{@code totalUsageLimit}, plus {@code perUserLimit}) where the
     * controller only ever wrote one member of each pair to the entity. We now expose
     * a single name per field — {@code type}/{@code value}/{@code maxUses} — matching
     * the names FE admin's {@code CouponWriteBody} sends and FE buyer code reads.
     */
    public record CreateCouponRequest(
            String code,
            String type,
            BigDecimal value,
            BigDecimal minOrderValue,
            BigDecimal maxDiscount,
            int maxUses,
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

    /**
     * Wire-format DTO replacing the previous habit of returning {@link CouponJpaEntity}
     * directly. Returning the entity leaked Hibernate proxy concerns and made the wire
     * shape implicit; this record is the single source of truth for what the API exposes.
     * Field names match the request DTO and FE schemas ({@code type}/{@code value}).
     */
    public record CouponResponse(
            Long id,
            String code,
            String type,
            BigDecimal value,
            BigDecimal minOrderValue,
            BigDecimal maxDiscount,
            int maxUses,
            int currentUses,
            boolean active,
            Instant validFrom,
            Instant validUntil) {

        public static CouponResponse from(CouponJpaEntity entity) {
            return new CouponResponse(
                    entity.getId(),
                    entity.getCode(),
                    entity.getDiscountType(),
                    entity.getDiscountValue(),
                    entity.getMinOrderValue(),
                    entity.getMaxDiscount(),
                    entity.getMaxUses(),
                    entity.getCurrentUses(),
                    entity.isActive(),
                    entity.getValidFrom(),
                    entity.getValidUntil()
            );
        }
    }
}
