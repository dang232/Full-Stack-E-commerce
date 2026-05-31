package com.vnshop.orderservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vnshop.orderservice.domain.SellerRevenuePoint;
import com.vnshop.orderservice.domain.port.out.DashboardAnalyticsPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GetSellerRevenueUseCaseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 17);
    private final Clock fixedClock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Test
    void aggregatesRevenuePerDayAndPadsMissingDaysWithZero() {
        StubAnalytics analytics = new StubAnalytics();
        analytics.sellerRows = List.of(
                new DashboardAnalyticsPort.SellerRevenueByDate(TODAY.minusDays(2), new BigDecimal("250000"), 3L),
                new DashboardAnalyticsPort.SellerRevenueByDate(TODAY, new BigDecimal("100000"), 1L)
        );
        GetSellerRevenueUseCase useCase = new GetSellerRevenueUseCase(analytics, fixedClock);

        List<SellerRevenuePoint> points = useCase.revenueForSeller("seller-1", 3);

        assertThat(analytics.lastSellerId).isEqualTo("seller-1");
        assertThat(analytics.lastStart).isEqualTo(TODAY.minusDays(2));
        assertThat(analytics.lastEnd).isEqualTo(TODAY);
        assertThat(points).extracting(SellerRevenuePoint::date)
                .containsExactly(TODAY.minusDays(2), TODAY.minusDays(1), TODAY);
        assertThat(points.get(0).revenue()).isEqualByComparingTo("250000");
        assertThat(points.get(0).orderCount()).isEqualTo(3L);
        // Day with no data is padded
        assertThat(points.get(1).revenue()).isEqualByComparingTo("0");
        assertThat(points.get(1).orderCount()).isZero();
        assertThat(points.get(2).revenue()).isEqualByComparingTo("100000");
        assertThat(points.get(2).orderCount()).isEqualTo(1L);
    }

    @Test
    void returnsAllZeroWindowWhenSellerHasNoOrders() {
        StubAnalytics analytics = new StubAnalytics();
        analytics.sellerRows = List.of();
        GetSellerRevenueUseCase useCase = new GetSellerRevenueUseCase(analytics, fixedClock);

        List<SellerRevenuePoint> points = useCase.revenueForSeller("seller-empty", 7);

        assertThat(points).hasSize(7);
        assertThat(points).allSatisfy(p -> {
            assertThat(p.revenue()).isEqualByComparingTo("0");
            assertThat(p.orderCount()).isZero();
        });
        assertThat(points.get(0).date()).isEqualTo(TODAY.minusDays(6));
        assertThat(points.get(6).date()).isEqualTo(TODAY);
    }

    @Test
    void rejectsBlankSellerId() {
        GetSellerRevenueUseCase useCase = new GetSellerRevenueUseCase(new StubAnalytics(), fixedClock);

        assertThatThrownBy(() -> useCase.revenueForSeller(" ", 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sellerId");
    }

    @Test
    void rejectsOutOfRangeDays() {
        GetSellerRevenueUseCase useCase = new GetSellerRevenueUseCase(new StubAnalytics(), fixedClock);

        assertThatThrownBy(() -> useCase.revenueForSeller("seller-1", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.revenueForSeller("seller-1", 366))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class StubAnalytics implements DashboardAnalyticsPort {
        List<SellerRevenueByDate> sellerRows = new ArrayList<>();
        String lastSellerId;
        LocalDate lastStart;
        LocalDate lastEnd;

        @Override
        public long countByDateBetween(LocalDate startDate, LocalDate endDate) {
            return 0;
        }

        @Override
        public BigDecimal sumRevenueByDateBetween(LocalDate startDate, LocalDate endDate) {
            return BigDecimal.ZERO;
        }

        @Override
        public long countDistinctBuyerId(LocalDate startDate, LocalDate endDate) {
            return 0;
        }

        @Override
        public long countDistinctSellerId(LocalDate startDate, LocalDate endDate) {
            return 0;
        }

        @Override
        public List<RevenueByDate> revenueByDateBetween(LocalDate startDate, LocalDate endDate) {
            return List.of();
        }

        @Override
        public List<TopMetric> topProducts(int limit) {
            return List.of();
        }

        @Override
        public List<TopMetric> topSellers(int limit) {
            return List.of();
        }

        @Override
        public List<SellerRevenueByDate> sellerRevenueByDateBetween(String sellerId, LocalDate startDate, LocalDate endDate) {
            this.lastSellerId = sellerId;
            this.lastStart = startDate;
            this.lastEnd = endDate;
            return sellerRows;
        }
    }
}
