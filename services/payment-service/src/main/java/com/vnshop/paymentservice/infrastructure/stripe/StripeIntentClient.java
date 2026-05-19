package com.vnshop.paymentservice.infrastructure.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;

/**
 * Thin seam over {@link com.stripe.model.PaymentIntent#create} so unit tests
 * can stub the SDK call. The default {@link DefaultStripeIntentClient} bound by
 * Spring delegates to the static SDK method.
 */
public interface StripeIntentClient {
    PaymentIntent create(PaymentIntentCreateParams params, RequestOptions options) throws StripeException;
}
