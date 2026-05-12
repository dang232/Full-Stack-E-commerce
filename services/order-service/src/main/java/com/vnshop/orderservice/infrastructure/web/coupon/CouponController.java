package com.vnshop.orderservice.infrastructure.web.coupon;

import com.vnshop.orderservice.application.coupon.ApplyCouponCommand;
import com.vnshop.orderservice.application.coupon.ApplyCouponResponse;
import com.vnshop.orderservice.application.coupon.ApplyCouponUseCase;
import com.vnshop.orderservice.application.coupon.CouponResponse;
import com.vnshop.orderservice.application.coupon.CreateCouponCommand;
import com.vnshop.orderservice.application.coupon.CreateCouponUseCase;
import com.vnshop.orderservice.application.coupon.ListActiveCouponsUseCase;
import com.vnshop.orderservice.application.coupon.ValidateCouponCommand;
import com.vnshop.orderservice.application.coupon.ValidateCouponResponse;
import com.vnshop.orderservice.application.coupon.ValidateCouponUseCase;
import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponException;
import com.vnshop.orderservice.domain.coupon.CouponId;
import com.vnshop.orderservice.domain.coupon.CouponRepository;
import com.vnshop.orderservice.domain.coupon.DiscountType;
import com.vnshop.orderservice.infrastructure.web.ApiResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CouponController {
    private final CreateCouponUseCase createCouponUseCase;
    private final ValidateCouponUseCase validateCouponUseCase;
    private final ApplyCouponUseCase applyCouponUseCase;
    private final ListActiveCouponsUseCase listActiveCouponsUseCase;
    private final CouponRepository couponRepository;

    public CouponController(
            CreateCouponUseCase createCouponUseCase,
            ValidateCouponUseCase validateCouponUseCase,
            ApplyCouponUseCase applyCouponUseCase,
            ListActiveCouponsUseCase listActiveCouponsUseCase,
            CouponRepository couponRepository
    ) {
        this.createCouponUseCase = createCouponUseCase;
        this.validateCouponUseCase = validateCouponUseCase;
        this.applyCouponUseCase = applyCouponUseCase;
        this.listActiveCouponsUseCase = listActiveCouponsUseCase;
        this.couponRepository = couponRepository;
    }

    @PostMapping("/admin/coupons")
    public ApiResponse<CouponResponse> create(@Valid @RequestBody CreateCouponRequest request) {
        return ApiResponse.ok(createCouponUseCase.create(request.toCommand()));
    }

    @GetMapping("/admin/coupons")
    public ApiResponse<List<CouponResponse>> adminList() {
        return ApiResponse.ok(listActiveCouponsUseCase.listAll());
    }

    @PutMapping("/admin/coupons/{id}")
    public ApiResponse<CouponResponse> update(@PathVariable UUID id, @Valid @RequestBody CreateCouponRequest request) {
        Coupon existing = couponRepository.findById(new CouponId(id))
                .orElseThrow(() -> new CouponException("COUPON_NOT_FOUND", "coupon not found"));
        Coupon updated = existing.withUpdatedDetails(
                DiscountType.valueOf(request.type()),
                request.value(),
                request.maxDiscount() == null ? null : new Money(request.maxDiscount()),
                request.minOrderValue() == null ? Money.ZERO : new Money(request.minOrderValue()),
                request.totalUsageLimit(),
                request.perUserLimit(),
                request.validFrom(),
                request.validUntil(),
                existing.active()
        );
        return ApiResponse.ok(toResponse(couponRepository.save(updated)));
    }

    @PostMapping("/admin/coupons/{id}/deactivate")
    public ApiResponse<CouponResponse> deactivate(@PathVariable UUID id) {
        Coupon coupon = couponRepository.findById(new CouponId(id))
                .orElseThrow(() -> new CouponException("COUPON_NOT_FOUND", "coupon not found"));
        coupon.deactivate();
        return ApiResponse.ok(toResponse(couponRepository.save(coupon)));
    }

    @PostMapping("/checkout/validate-coupon")
    public ApiResponse<ValidateCouponResponse> validate(@Valid @RequestBody ValidateCouponRequest request) {
        return ApiResponse.ok(validateCouponUseCase.validate(request.toCommand()));
    }

    @PostMapping("/checkout/apply-coupon")
    public ApiResponse<ApplyCouponResponse> apply(@Valid @RequestBody ApplyCouponRequest request) {
        return ApiResponse.ok(applyCouponUseCase.apply(request.toCommand()));
    }

    @GetMapping("/coupons")
    public ApiResponse<List<CouponResponse>> publicList() {
        return ApiResponse.ok(listActiveCouponsUseCase.listActive());
    }

    private static CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.id().value(),
                coupon.code(),
                coupon.type().name(),
                coupon.value(),
                coupon.type().name() + " discount",
                coupon.minOrderValue().amount(),
                coupon.validUntil(),
                Math.max(0, coupon.totalUsageLimit() - coupon.totalUsed())
        );
    }

    public record CreateCouponRequest(
            String code,
            String type,
            BigDecimal value,
            BigDecimal maxDiscount,
            BigDecimal minOrderValue,
            int totalUsageLimit,
            int perUserLimit,
            LocalDateTime validFrom,
            LocalDateTime validUntil
    ) {
        CreateCouponCommand toCommand() {
            return new CreateCouponCommand(code, type, value, maxDiscount, minOrderValue, totalUsageLimit, perUserLimit, validFrom, validUntil);
        }
    }

    public record ValidateCouponRequest(String code, BigDecimal orderTotal, String userId) {
        ValidateCouponCommand toCommand() {
            return new ValidateCouponCommand(code, orderTotal, userId);
        }
    }

    public record ApplyCouponRequest(String code, UUID orderId, String userId, BigDecimal orderTotal) {
        ApplyCouponCommand toCommand() {
            return new ApplyCouponCommand(code, orderId, userId, orderTotal);
        }
    }
}
