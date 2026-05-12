package com.vnshop.orderservice.domain.coupon;

import com.vnshop.orderservice.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 12, 0, 0);

    @Test
    void createNormalizesCodeAndStartsActiveWithNoUsage() {
        Coupon coupon = percentageCoupon(" welcome 10 ", BigDecimal.TEN, null, BigDecimal.valueOf(100000), 10, 2, NOW.minusDays(1), NOW.plusDays(1));

        assertThat(coupon.code()).isEqualTo("WELCOME10");
        assertThat(coupon.active()).isTrue();
        assertThat(coupon.totalUsed()).isZero();
        assertThat(coupon.isValid(new Money(BigDecimal.valueOf(200000)), 0, NOW)).isTrue();
    }

    @Test
    void createRejectsInvalidPercentageAndDateWindow() {
        assertThatThrownBy(() -> percentageCoupon("BAD", BigDecimal.valueOf(101), null, BigDecimal.ZERO, 10, 1, NOW.minusDays(1), NOW.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percentage discount");

        assertThatThrownBy(() -> fixedCoupon("BADDATE", BigDecimal.valueOf(10000), BigDecimal.ZERO, 10, 1, NOW.plusDays(1), NOW.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validUntil");
    }

    @Test
    void invalidCodeReportsExpiredExhaustedUserLimitMinOrderAndInactive() {
        assertThat(validCoupon().invalidCode(new Money(BigDecimal.valueOf(200000)), 0, NOW.plusDays(2))).isEqualTo("COUPON_EXPIRED");

        Coupon exhausted = fixedCoupon("ONCE", BigDecimal.valueOf(10000), BigDecimal.ZERO, 1, 2, NOW.minusDays(1), NOW.plusDays(1));
        exhausted.recordUsage();
        assertThat(exhausted.invalidCode(new Money(BigDecimal.valueOf(200000)), 0, NOW)).isEqualTo("COUPON_EXHAUSTED");

        assertThat(validCoupon().invalidCode(new Money(BigDecimal.valueOf(200000)), 1, NOW)).isEqualTo("COUPON_USER_LIMIT");
        assertThat(validCoupon().invalidCode(new Money(BigDecimal.valueOf(99999)), 0, NOW)).isEqualTo("COUPON_MIN_ORDER");

        Coupon inactive = validCoupon();
        inactive.deactivate();
        assertThat(inactive.invalidCode(new Money(BigDecimal.valueOf(200000)), 0, NOW)).isEqualTo("COUPON_INACTIVE");
    }

    @Test
    void calculateDiscountSupportsPercentageFixedCapsAndFreeShipping() {
        Money orderTotal = new Money(BigDecimal.valueOf(200000));

        assertThat(percentageCoupon("PCT", BigDecimal.TEN, null, BigDecimal.ZERO, 10, 1, NOW.minusDays(1), NOW.plusDays(1)).calculateDiscount(orderTotal).amount())
                .isEqualByComparingTo("20000");
        assertThat(fixedCoupon("FIX", BigDecimal.valueOf(50000), BigDecimal.ZERO, 10, 1, NOW.minusDays(1), NOW.plusDays(1)).calculateDiscount(orderTotal).amount())
                .isEqualByComparingTo("50000");
        assertThat(fixedCoupon("BIGFIX", BigDecimal.valueOf(250000), BigDecimal.ZERO, 10, 1, NOW.minusDays(1), NOW.plusDays(1)).calculateDiscount(orderTotal).amount())
                .isEqualByComparingTo("200000");
        assertThat(percentageCoupon("CAP", BigDecimal.valueOf(50), new Money(BigDecimal.valueOf(30000)), BigDecimal.ZERO, 10, 1, NOW.minusDays(1), NOW.plusDays(1)).calculateDiscount(orderTotal).amount())
                .isEqualByComparingTo("30000");
        assertThat(freeShippingCoupon().calculateDiscount(orderTotal)).isEqualTo(Money.ZERO);
    }

    @Test
    void releaseUsageNeverDropsBelowZero() {
        Coupon coupon = validCoupon();

        coupon.releaseUsage();
        assertThat(coupon.totalUsed()).isZero();

        coupon.recordUsage();
        coupon.releaseUsage();
        assertThat(coupon.totalUsed()).isZero();
    }

    private static Coupon validCoupon() {
        return fixedCoupon("VALID", BigDecimal.valueOf(10000), BigDecimal.valueOf(100000), 10, 1, NOW.minusDays(1), NOW.plusDays(1));
    }

    private static Coupon fixedCoupon(String code, BigDecimal value, BigDecimal minOrderValue, int totalLimit, int perUserLimit, LocalDateTime from, LocalDateTime until) {
        return Coupon.create(CouponId.generate(), code, DiscountType.FIXED, value, null, new Money(minOrderValue), totalLimit, perUserLimit, from, until);
    }

    private static Coupon percentageCoupon(String code, BigDecimal value, Money maxDiscount, BigDecimal minOrderValue, int totalLimit, int perUserLimit, LocalDateTime from, LocalDateTime until) {
        return Coupon.create(CouponId.generate(), code, DiscountType.PERCENTAGE, value, maxDiscount, new Money(minOrderValue), totalLimit, perUserLimit, from, until);
    }

    private static Coupon freeShippingCoupon() {
        return Coupon.create(CouponId.generate(), "SHIP", DiscountType.FREE_SHIPPING, BigDecimal.ZERO, null, Money.ZERO, 10, 1, NOW.minusDays(1), NOW.plusDays(1));
    }
}
