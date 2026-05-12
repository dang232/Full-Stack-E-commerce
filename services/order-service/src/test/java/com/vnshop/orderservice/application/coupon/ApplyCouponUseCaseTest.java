package com.vnshop.orderservice.application.coupon;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponException;
import com.vnshop.orderservice.domain.coupon.CouponId;
import com.vnshop.orderservice.domain.coupon.CouponRepository;
import com.vnshop.orderservice.domain.coupon.CouponUsageRepository;
import com.vnshop.orderservice.domain.coupon.CouponValidator;
import com.vnshop.orderservice.domain.coupon.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplyCouponUseCaseTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 12, 0, 0);

    private final CouponValidator couponValidator = mock(CouponValidator.class);
    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final CouponUsageRepository usageRepository = mock(CouponUsageRepository.class);
    private final ApplyCouponUseCase useCase = new ApplyCouponUseCase(couponValidator, couponRepository, usageRepository);

    @Test
    void appliesCouponAndRecordsTotalAndPerUserUsageAtomically() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Coupon coupon = coupon();
        when(couponValidator.validate("save10", new Money(BigDecimal.valueOf(200000)), "buyer-a"))
                .thenReturn(new CouponValidator.ValidationResult(true, coupon, new Money(BigDecimal.valueOf(20000)), null, null));
        when(couponRepository.save(coupon)).thenReturn(coupon);

        ApplyCouponResponse response = useCase.apply(new ApplyCouponCommand("save10", orderId, "buyer-a", BigDecimal.valueOf(200000)));

        assertThat(response.discount().amount()).isEqualByComparingTo("20000");
        assertThat(response.newTotal().amount()).isEqualByComparingTo("180000");
        assertThat(coupon.totalUsed()).isEqualTo(1);
        InOrder inOrder = inOrder(couponRepository, usageRepository);
        inOrder.verify(couponRepository).save(coupon);
        inOrder.verify(usageRepository).recordUsage(coupon.id(), "buyer-a", orderId);
    }

    @Test
    void doesNotRecordUsageWhenValidationFails() {
        Coupon coupon = coupon();
        when(couponValidator.validate("save10", new Money(BigDecimal.valueOf(50000)), "buyer-a"))
                .thenReturn(new CouponValidator.ValidationResult(false, coupon, Money.ZERO, "Minimum order value not met", "COUPON_MIN_ORDER"));

        assertThatThrownBy(() -> useCase.apply(new ApplyCouponCommand("save10", UUID.randomUUID(), "buyer-a", BigDecimal.valueOf(50000))))
                .isInstanceOf(CouponException.class)
                .satisfies(exception -> assertThat(((CouponException) exception).code()).isEqualTo("COUPON_MIN_ORDER"));
        verify(couponRepository, never()).save(coupon);
        verify(usageRepository, never()).recordUsage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    private static Coupon coupon() {
        return Coupon.create(CouponId.generate(), "SAVE10", DiscountType.PERCENTAGE, BigDecimal.TEN, null, new Money(BigDecimal.valueOf(100000)), 10, 1, NOW.minusDays(1), NOW.plusDays(1));
    }
}
