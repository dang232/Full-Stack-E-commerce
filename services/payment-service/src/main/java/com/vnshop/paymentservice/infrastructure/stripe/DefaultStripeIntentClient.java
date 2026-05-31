package com.vnshop.paymentservice.infrastructure.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.stripe.enabled", havingValue = "true")
public class DefaultStripeIntentClient implements StripeIntentClient {
    @Override
    public PaymentIntent create(PaymentIntentCreateParams params, RequestOptions options) throws StripeException {
        return PaymentIntent.create(params, options);
    }
}
