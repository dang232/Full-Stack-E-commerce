package com.vnshop.orderservice.application.coupon;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponId;
import com.vnshop.orderservice.domain.coupon.CouponRepository;
import com.vnshop.orderservice.domain.coupon.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListActiveCouponsUseCaseTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 12, 0, 0);

    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final ListActiveCouponsUseCase useCase = new ListActiveCouponsUseCase(couponRepository);

    @Test
    void listsActiveCouponsAsResponses() {
        Coupon coupon = coupon("ACTIVE10", 1);
        when(couponRepository.findActive()).thenReturn(List.of(coupon));

        List<CouponResponse> responses = useCase.listActive();

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(coupon.id().value());
            assertThat(response.code()).isEqualTo("ACTIVE10");
            assertThat(response.type()).isEqualTo("PERCENTAGE");
            assertThat(response.value()).isEqualByComparingTo("10");
            assertThat(response.description()).isEqualTo("PERCENTAGE discount");
            assertThat(response.minOrderValue()).isEqualByComparingTo("100000");
            assertThat(response.validUntil()).isEqualTo(NOW.plusDays(1));
            assertThat(response.remainingUses()).isEqualTo(9);
        });
    }

    @Test
    void listsAllCouponsAsResponsesAndClampsRemainingUsesAtZero() {
        Coupon coupon = coupon("USEDUP", 12);
        when(couponRepository.findAll()).thenReturn(List.of(coupon));

        List<CouponResponse> responses = useCase.listAll();

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.code()).isEqualTo("USEDUP");
            assertThat(response.remainingUses()).isZero();
        });
    }

    private static Coupon coupon(String code, int totalUsed) {
        return Coupon.restore(
                CouponId.generate(),
                code,
                DiscountType.PERCENTAGE,
                BigDecimal.TEN,
                null,
                new Money(BigDecimal.valueOf(100000)),
                10,
                totalUsed,
                1,
                NOW.minusDays(1),
                NOW.plusDays(1),
                true,
                NOW.minusDays(2)
        );
    }
}
