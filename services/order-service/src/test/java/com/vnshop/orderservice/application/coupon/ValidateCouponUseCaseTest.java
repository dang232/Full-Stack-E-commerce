package com.vnshop.orderservice.application.coupon;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponException;
import com.vnshop.orderservice.domain.coupon.CouponId;
import com.vnshop.orderservice.domain.coupon.CouponValidator;
import com.vnshop.orderservice.domain.coupon.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ValidateCouponUseCaseTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 12, 0, 0);

    private final CouponValidator couponValidator = mock(CouponValidator.class);
    private final ValidateCouponUseCase useCase = new ValidateCouponUseCase(couponValidator);

    @Test
    void returnsValidResponseWithoutMutatingCouponUsage() {
        Coupon coupon = coupon();
        when(couponValidator.validate("save10", new Money(BigDecimal.valueOf(200000)), "buyer-a"))
                .thenReturn(new CouponValidator.ValidationResult(true, coupon, new Money(BigDecimal.valueOf(20000)), null, null));

        ValidateCouponResponse response = useCase.validate(new ValidateCouponCommand("save10", BigDecimal.valueOf(200000), "buyer-a"));

        assertThat(response.valid()).isTrue();
        assertThat(response.code()).isEqualTo("SAVE10");
        assertThat(response.discount()).isEqualByComparingTo("20000");
        assertThat(coupon.totalUsed()).isZero();
    }

    @Test
    void throwsCouponExceptionForInvalidValidationResultWithoutRecordingUsage() {
        Coupon coupon = coupon();
        when(couponValidator.validate("save10", new Money(BigDecimal.valueOf(50000)), "buyer-a"))
                .thenReturn(new CouponValidator.ValidationResult(false, coupon, Money.ZERO, "Minimum order value not met", "COUPON_MIN_ORDER"));

        assertThatThrownBy(() -> useCase.validate(new ValidateCouponCommand("save10", BigDecimal.valueOf(50000), "buyer-a")))
                .isInstanceOf(CouponException.class)
                .satisfies(exception -> assertThat(((CouponException) exception).code()).isEqualTo("COUPON_MIN_ORDER"));
        assertThat(coupon.totalUsed()).isZero();
    }

    private static Coupon coupon() {
        return Coupon.create(CouponId.generate(), "SAVE10", DiscountType.PERCENTAGE, BigDecimal.TEN, null, new Money(BigDecimal.valueOf(100000)), 10, 1, NOW.minusDays(1), NOW.plusDays(1));
    }
}
