package com.vnshop.orderservice.application.fraud;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.port.out.FraudOrderCountPort;
import com.vnshop.orderservice.domain.port.out.OutboxPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock FraudOrderCountPort fraudOrderCountPort;
    @Mock OutboxPort outboxPort;
    @Mock Order order;

    private FraudDetectionService service;

    @BeforeEach
    void setUp() {
        service = new FraudDetectionService(
                fraudOrderCountPort, outboxPort, new GeoIpService());
        when(order.id()).thenReturn(UUID.randomUUID());
        when(order.buyerId()).thenReturn("buyer-1");
    }

    @Test
    void clean_whenNoRulesTriggered() {
        when(fraudOrderCountPort.countRecentOrders(anyString(), any(Instant.class))).thenReturn(0L);
        when(order.finalAmount()).thenReturn(money(500_000));

        FraudCheckResult result = service.evaluate(order, null, null, "VN");

        assertThat(result.flagged()).isFalse();
        assertThat(result.reasons()).isEmpty();
        verifyNoInteractions(outboxPort);
    }

    @Test
    void flagged_whenVelocityExceeds3OrdersPerHour() {
        when(fraudOrderCountPort.countRecentOrders(anyString(), any(Instant.class))).thenReturn(3L);
        when(order.finalAmount()).thenReturn(money(500_000));

        FraudCheckResult result = service.evaluate(order, null, null, "VN");

        assertThat(result.flagged()).isTrue();
        assertThat(result.reasons()).anyMatch(r -> r.startsWith("VELOCITY"));
        verify(outboxPort).publish(anyString(), anyString(), eq("ORDER_FRAUD_FLAGGED"), anyString());
    }

    @Test
    void flagged_whenAmountExceedsThreshold() {
        when(fraudOrderCountPort.countRecentOrders(anyString(), any(Instant.class))).thenReturn(0L);
        when(order.finalAmount()).thenReturn(money(15_000_000));

        FraudCheckResult result = service.evaluate(order, null, null, "VN");

        assertThat(result.flagged()).isTrue();
        assertThat(result.reasons()).anyMatch(r -> r.startsWith("AMOUNT"));
        verify(outboxPort).publish(anyString(), anyString(), eq("ORDER_FRAUD_FLAGGED"), anyString());
    }

    @Test
    void notFlagged_whenAmountEqualsThreshold() {
        // threshold is strictly > 10_000_000, so exactly 10_000_000 must not flag
        when(fraudOrderCountPort.countRecentOrders(anyString(), any(Instant.class))).thenReturn(0L);
        when(order.finalAmount()).thenReturn(money(10_000_000));

        FraudCheckResult result = service.evaluate(order, null, null, "VN");

        assertThat(result.flagged()).isFalse();
    }

    @Test
    void flagged_withBothVelocityAndAmount_hasTwoReasons() {
        when(fraudOrderCountPort.countRecentOrders(anyString(), any(Instant.class))).thenReturn(5L);
        when(order.finalAmount()).thenReturn(money(20_000_000));

        FraudCheckResult result = service.evaluate(order, null, null, "VN");

        assertThat(result.flagged()).isTrue();
        assertThat(result.reasons()).hasSize(2);
        assertThat(result.reasons()).anyMatch(r -> r.startsWith("VELOCITY"));
        assertThat(result.reasons()).anyMatch(r -> r.startsWith("AMOUNT"));
    }

    @Test
    void clean_whenVelocityBelowThreshold() {
        // exactly 2 orders in window — should not trigger (max is 3, condition is >=3)
        when(fraudOrderCountPort.countRecentOrders(anyString(), any(Instant.class))).thenReturn(2L);
        when(order.finalAmount()).thenReturn(money(500_000));

        FraudCheckResult result = service.evaluate(order, null, null, "VN");

        assertThat(result.flagged()).isFalse();
        assertThat(result.reasons()).isEmpty();
    }

    // ---- helpers --------------------------------------------------------

    private static Money money(long vnd) {
        return new Money(BigDecimal.valueOf(vnd), "VND");
    }
}
