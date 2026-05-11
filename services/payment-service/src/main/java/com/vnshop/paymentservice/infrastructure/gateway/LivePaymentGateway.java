package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Primary
@Component
@ConditionalOnProperty(name = "payment.mode", havingValue = "live")
public class LivePaymentGateway implements PaymentGatewayPort {
    private final VnpayGateway vnpayGateway;
    private final MomoGateway momoGateway;
    public LivePaymentGateway(VnpayGateway vnpayGateway, MomoGateway momoGateway) {
        this.vnpayGateway = Objects.requireNonNull(vnpayGateway, "vnpayGateway is required");
        this.momoGateway = Objects.requireNonNull(momoGateway, "momoGateway is required");
    }

    @Override
    public GatewayPaymentResult processPayment(Payment payment) {
        return switch (payment.method()) {
            case COD -> completeCodPayment(payment);
            case VNPAY -> new GatewayPaymentResult(PaymentStatus.PENDING, vnpayGateway.createPaymentUrl(payment, "0.0.0.0"));
            case MOMO -> createMomoPayment(payment);
        };
    }

    @Override
    public PaymentStatus getStatus(String paymentId) {
        return PaymentStatus.PENDING;
    }

    private GatewayPaymentResult createMomoPayment(Payment payment) {
        MomoGateway.PaymentGatewayResult result = momoGateway.processPayment(payment);
        return new GatewayPaymentResult(result.status(), result.transactionRef());
    }

    private GatewayPaymentResult completeCodPayment(Payment payment) {
        String transactionRef = "COD-" + payment.paymentId();
        return new GatewayPaymentResult(PaymentStatus.COMPLETED, transactionRef);
    }
}
