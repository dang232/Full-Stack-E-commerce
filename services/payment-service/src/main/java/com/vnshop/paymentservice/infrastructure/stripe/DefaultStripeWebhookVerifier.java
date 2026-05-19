package com.vnshop.paymentservice.infrastructure.stripe;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.stripe.enabled", havingValue = "true")
public class DefaultStripeWebhookVerifier implements StripeWebhookVerifier {
    @Override
    public Event constructEvent(String payload, String signatureHeader, String webhookSecret) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, signatureHeader, webhookSecret);
    }
}
