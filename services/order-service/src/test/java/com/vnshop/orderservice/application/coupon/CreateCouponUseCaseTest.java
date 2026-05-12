package com.vnshop.orderservice.application.coupon;

import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponException;
import com.vnshop.orderservice.domain.coupon.CouponRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateCouponUseCaseTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 12, 0, 0);

    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final CreateCouponUseCase useCase = new CreateCouponUseCase(couponRepository);

    @Test
    void createsNormalizedCouponAndReturnsResponse() {
        when(couponRepository.existsByCode("WELCOME10")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CouponResponse response = useCase.create(command(" welcome 10 ", "PERCENTAGE", "10", "30000", "100000"));

        assertThat(response.code()).isEqualTo("WELCOME10");
        assertThat(response.type()).isEqualTo("PERCENTAGE");
        assertThat(response.remainingUses()).isEqualTo(10);
        verify(couponRepository).existsByCode("WELCOME10");
        verify(couponRepository).save(any(Coupon.class));
    }

    @Test
    void rejectsDuplicateNormalizedCode() {
        when(couponRepository.existsByCode("DUPE")).thenReturn(true);

        assertThatThrownBy(() -> useCase.create(command("du pe", "FIXED", "10000", null, "100000")))
                .isInstanceOf(CouponException.class)
                .satisfies(exception -> assertThat(((CouponException) exception).code()).isEqualTo("COUPON_CODE_DUPLICATE"));
        verify(couponRepository, never()).save(any(Coupon.class));
    }

    private static CreateCouponCommand command(String code, String type, String value, String maxDiscount, String minOrderValue) {
        return new CreateCouponCommand(
                code,
                type,
                new BigDecimal(value),
                maxDiscount == null ? null : new BigDecimal(maxDiscount),
                new BigDecimal(minOrderValue),
                10,
                2,
                NOW.minusDays(1),
                NOW.plusDays(1)
        );
    }
}
