package com.vnshop.paymentservice.infrastructure.stripe;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;

/**
 * Thin seam over {@link Webhook#constructEvent} so unit tests can stub
 * signature verification without instantiating the static SDK helper or
 * generating real signatures. Production wires {@link DefaultStripeWebhookVerifier}
 * which delegates straight to the SDK.
 */
public interface StripeWebhookVerifier {
    Event constructEvent(String payload, String signatureHeader, String webhookSecret) throws SignatureVerificationException;

    /**
     * Pulls the {@link PaymentIntent} payload off a {@code payment_intent.*}
     * event. Returns {@code null} when the SDK can't deserialise (typically
     * means the event API version doesn't match the SDK pin — a sandbox-only
     * concern that we surface as a 400 rather than throwing).
     */
    static PaymentIntent paymentIntent(Event event) {
        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        return obj instanceof PaymentIntent intent ? intent : null;
    }
}
