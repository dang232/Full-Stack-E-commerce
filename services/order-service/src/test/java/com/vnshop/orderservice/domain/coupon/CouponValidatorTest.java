package com.vnshop.orderservice.domain.coupon;

import com.vnshop.orderservice.domain.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponValidatorTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-12T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());

    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final CouponUsageRepository usageRepository = mock(CouponUsageRepository.class);
    private final CouponValidator validator = new CouponValidator(couponRepository, usageRepository, CLOCK);

    @Test
    void validatesActiveCouponAndCalculatesDiscount() {
        Coupon coupon = coupon("SAVE10", NOW.minusDays(1), NOW.plusDays(1));
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));
        when(usageRepository.getUsageCount(coupon.id(), "buyer-a")).thenReturn(0);

        CouponValidator.ValidationResult result = validator.validate(" save 10 ", new Money(BigDecimal.valueOf(200000)), "buyer-a");

        assertThat(result.valid()).isTrue();
        assertThat(result.discount().amount()).isEqualByComparingTo("20000");
        assertThat(result.errorCode()).isNull();
        verify(couponRepository).findByCode("SAVE10");
    }

    @Test
    void returnsExpectedInvalidCodesForExpiredExhaustedUserLimitMinOrderAndInactive() {
        assertInvalid(coupon("OLD", NOW.minusDays(5), NOW.minusDays(1)), 0, BigDecimal.valueOf(200000), "COUPON_EXPIRED");

        Coupon exhausted = coupon("USED", NOW.minusDays(1), NOW.plusDays(1));
        exhausted.recordUsage();
        exhausted.recordUsage();
        assertInvalid(exhausted, 0, BigDecimal.valueOf(200000), "COUPON_EXHAUSTED");

        assertInvalid(coupon("USER", NOW.minusDays(1), NOW.plusDays(1)), 2, BigDecimal.valueOf(200000), "COUPON_USER_LIMIT");
        assertInvalid(coupon("MIN", NOW.minusDays(1), NOW.plusDays(1)), 0, BigDecimal.valueOf(99999), "COUPON_MIN_ORDER");

        Coupon inactive = coupon("OFF", NOW.minusDays(1), NOW.plusDays(1));
        inactive.deactivate();
        assertInvalid(inactive, 0, BigDecimal.valueOf(200000), "COUPON_INACTIVE");
    }

    @Test
    void throwsWhenCouponMissingWithoutReadingUsage() {
        when(couponRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate("missing", new Money(BigDecimal.valueOf(200000)), "buyer-a"))
                .isInstanceOf(CouponException.class)
                .satisfies(exception -> assertThat(((CouponException) exception).code()).isEqualTo("COUPON_NOT_FOUND"));
        verify(usageRepository, never()).getUsageCount(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    private void assertInvalid(Coupon coupon, int userUsage, BigDecimal orderTotal, String errorCode) {
        when(couponRepository.findByCode(coupon.code())).thenReturn(Optional.of(coupon));
        when(usageRepository.getUsageCount(coupon.id(), "buyer-a")).thenReturn(userUsage);

        CouponValidator.ValidationResult result = validator.validate(coupon.code(), new Money(orderTotal), "buyer-a");

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(errorCode);
    }

    private static Coupon coupon(String code, LocalDateTime from, LocalDateTime until) {
        return Coupon.create(CouponId.generate(), code, DiscountType.PERCENTAGE, BigDecimal.TEN, null, new Money(BigDecimal.valueOf(100000)), 2, 2, from, until);
    }
}
