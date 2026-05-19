package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(name = "payment.vnpay.enabled", havingValue = "true")
public class VnpayPaymentMethodHandler implements PaymentMethodHandler {
    private final VnpayGateway vnpayGateway;

    public VnpayPaymentMethodHandler(VnpayGateway vnpayGateway) {
        this.vnpayGateway = Objects.requireNonNull(vnpayGateway, "vnpayGateway is required");
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.VNPAY;
    }

    @Override
    public PaymentGatewayPort.GatewayPaymentResult processPayment(Payment payment) {
        return new PaymentGatewayPort.GatewayPaymentResult(
                PaymentStatus.PENDING,
                vnpayGateway.createPaymentUrl(payment, "0.0.0.0"));
    }
}
