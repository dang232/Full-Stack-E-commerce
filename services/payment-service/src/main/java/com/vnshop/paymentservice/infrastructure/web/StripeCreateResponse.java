package com.vnshop.paymentservice.infrastructure.web;

import com.vnshop.paymentservice.domain.Payment;

import java.math.BigDecimal;

/**
 * Response shape for {@code POST /payment/stripe/create}. Matches the FE
 * Elements integration: {@code clientSecret} is what the {@code <Elements>}
 * provider needs; the rest is reconciliation/audit metadata the FE can echo
 * back on the success page.
 */
public record StripeCreateResponse(
        PaymentResponse payment,
        String publishableKey,
        String clientSecret,
        String intentId,
        BigDecimal externalAmount,
        String externalCurrency,
        BigDecimal fxRate) {
    public static StripeCreateResponse of(
            Payment payment,
            String publishableKey,
            String clientSecret,
            String intentId,
            BigDecimal externalAmount,
            String externalCurrency,
            BigDecimal fxRate) {
        return new StripeCreateResponse(
                PaymentResponse.fromDomain(payment),
                publishableKey,
                clientSecret,
                intentId,
                externalAmount,
                externalCurrency,
                fxRate);
    }
}
