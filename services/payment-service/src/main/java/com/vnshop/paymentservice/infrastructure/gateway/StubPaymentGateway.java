package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.mode", havingValue = "stub", matchIfMissing = true)
public class StubPaymentGateway implements PaymentGatewayPort {
    @Override
    public GatewayPaymentResult processPayment(Payment payment) {
        return switch (payment.method()) {
            case COD -> completeCodPayment(payment);
            case VNPAY -> new GatewayPaymentResult(PaymentStatus.PENDING, "stub://vnpay/redirect/" + payment.paymentId());
            case MOMO -> new GatewayPaymentResult(PaymentStatus.PENDING, "stub://momo/redirect/" + payment.paymentId());
        };
    }

    private GatewayPaymentResult completeCodPayment(Payment payment) {
        return new GatewayPaymentResult(PaymentStatus.COMPLETED, "COD-" + payment.paymentId());
    }

    @Override
    public PaymentStatus getStatus(String paymentId) {
        return PaymentStatus.PENDING;
    }
}
