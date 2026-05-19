package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(name = "payment.momo.enabled", havingValue = "true")
public class MomoPaymentMethodHandler implements PaymentMethodHandler {
    private final MomoGateway momoGateway;

    public MomoPaymentMethodHandler(MomoGateway momoGateway) {
        this.momoGateway = Objects.requireNonNull(momoGateway, "momoGateway is required");
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.MOMO;
    }

    @Override
    public PaymentGatewayPort.GatewayPaymentResult processPayment(Payment payment) {
        MomoGateway.PaymentGatewayResult result = momoGateway.processPayment(payment);
        return new PaymentGatewayPort.GatewayPaymentResult(result.status(), result.transactionRef());
    }
}
