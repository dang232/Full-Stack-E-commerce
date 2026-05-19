package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositePaymentGatewayTest {

    @Test
    void dispatchesToEnabledHandler() {
        StubHandler cod = new StubHandler(PaymentMethod.COD, PaymentStatus.COMPLETED, "COD-OK");
        CompositePaymentGateway gateway = new CompositePaymentGateway(List.of(cod));

        PaymentGatewayPort.GatewayPaymentResult result = gateway.processPayment(payment(PaymentMethod.COD));

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.transactionRef()).isEqualTo("COD-OK");
        assertThat(gateway.isMethodEnabled(PaymentMethod.COD)).isTrue();
    }

    @Test
    void returnsMethodDisabledWhenHandlerAbsent() {
        StubHandler cod = new StubHandler(PaymentMethod.COD, PaymentStatus.COMPLETED, "COD-OK");
        CompositePaymentGateway gateway = new CompositePaymentGateway(List.of(cod));

        PaymentGatewayPort.GatewayPaymentResult result = gateway.processPayment(payment(PaymentMethod.STRIPE));

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.transactionRef()).isEqualTo("METHOD_DISABLED");
        assertThat(gateway.isMethodEnabled(PaymentMethod.STRIPE)).isFalse();
    }

    @Test
    void rejectsDuplicateHandlersForSameMethod() {
        StubHandler a = new StubHandler(PaymentMethod.COD, PaymentStatus.COMPLETED, "A");
        StubHandler b = new StubHandler(PaymentMethod.COD, PaymentStatus.COMPLETED, "B");

        assertThatThrownBy(() -> new CompositePaymentGateway(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple handlers registered for COD");
    }

    private static Payment payment(PaymentMethod method) {
        return new Payment(UUID.randomUUID(), "ORDER-1", "BUYER-1",
                new BigDecimal("100000.00"), method, PaymentStatus.PENDING, null, Instant.now());
    }

    private static final class StubHandler implements PaymentMethodHandler {
        private final PaymentMethod method;
        private final PaymentGatewayPort.GatewayPaymentResult result;

        StubHandler(PaymentMethod method, PaymentStatus status, String ref) {
            this.method = method;
            this.result = new PaymentGatewayPort.GatewayPaymentResult(status, ref);
        }

        @Override
        public PaymentMethod method() {
            return method;
        }

        @Override
        public PaymentGatewayPort.GatewayPaymentResult processPayment(Payment payment) {
            return result;
        }
    }
}
