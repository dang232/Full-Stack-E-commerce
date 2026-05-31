package com.vnshop.paymentservice.infrastructure.paypal;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import com.vnshop.paymentservice.infrastructure.gateway.PaymentMethodHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.paypal.enabled", havingValue = "true")
public class PayPalPaymentMethodHandler implements PaymentMethodHandler {
    @Override
    public PaymentMethod method() {
        return PaymentMethod.PAYPAL;
    }

    @Override
    public PaymentGatewayPort.GatewayPaymentResult processPayment(Payment payment) {
        return new PaymentGatewayPort.GatewayPaymentResult(PaymentStatus.PENDING, "PAYPAL-" + payment.paymentId());
    }
}
