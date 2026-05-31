package com.vnshop.paymentservice.infrastructure.gateway;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.port.out.PaymentGatewayPort;

/**
 * One bean per payment method. Each handler is gated by
 * {@code @ConditionalOnProperty(payment.<method>.enabled=true)} — disabling a
 * method removes its handler from the context and {@link CompositePaymentGateway}
 * surfaces a {@code METHOD_DISABLED} failure instead.
 */
public interface PaymentMethodHandler {
    PaymentMethod method();

    PaymentGatewayPort.GatewayPaymentResult processPayment(Payment payment);
}
