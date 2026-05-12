package com.vnshop.orderservice.application.coupon;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponId;
import com.vnshop.orderservice.domain.coupon.CouponRepository;
import com.vnshop.orderservice.domain.coupon.CouponUsageRepository;
import com.vnshop.orderservice.domain.coupon.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseCouponUsageUseCaseTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 12, 0, 0);

    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final CouponUsageRepository usageRepository = mock(CouponUsageRepository.class);
    private final ReleaseCouponUsageUseCase useCase = new ReleaseCouponUsageUseCase(couponRepository, usageRepository);

    @Test
    void releasesCouponUsageAndUserUsageWhenCouponExists() {
        Coupon coupon = coupon();
        coupon.recordUsage();
        CouponId couponId = coupon.id();
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));

        useCase.release(couponId, "buyer-a");

        assertThat(coupon.totalUsed()).isZero();
        verify(couponRepository).save(coupon);
        verify(usageRepository).releaseUsage(couponId, "buyer-a");
    }

    @Test
    void skipsReleaseWhenCouponIsMissing() {
        CouponId couponId = CouponId.generate();
        when(couponRepository.findById(couponId)).thenReturn(Optional.empty());

        useCase.release(couponId, "buyer-a");

        verify(couponRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(usageRepository, never()).releaseUsage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void releasesCouponFoundForOrder() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Coupon coupon = coupon();
        coupon.recordUsage();
        when(usageRepository.releaseUsageForOrder(orderId, "buyer-a")).thenReturn(Optional.of(coupon.id()));
        when(couponRepository.findById(coupon.id())).thenReturn(Optional.of(coupon));

        useCase.releaseForOrder(orderId, "buyer-a");

        assertThat(coupon.totalUsed()).isZero();
        verify(couponRepository).save(coupon);
    }

    @Test
    void skipsCouponMutationWhenOrderHasNoCouponUsage() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(usageRepository.releaseUsageForOrder(orderId, "buyer-a")).thenReturn(Optional.empty());

        useCase.releaseForOrder(orderId, "buyer-a");

        verify(couponRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(couponRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsSavingWhenOrderCouponNoLongerExists() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        CouponId couponId = CouponId.generate();
        when(usageRepository.releaseUsageForOrder(orderId, "buyer-a")).thenReturn(Optional.of(couponId));
        when(couponRepository.findById(couponId)).thenReturn(Optional.empty());

        useCase.releaseForOrder(orderId, "buyer-a");

        verify(couponRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static Coupon coupon() {
        return Coupon.create(CouponId.generate(), "SAVE10", DiscountType.PERCENTAGE, BigDecimal.TEN, null, new Money(BigDecimal.valueOf(100000)), 10, 1, NOW.minusDays(1), NOW.plusDays(1));
    }
}
