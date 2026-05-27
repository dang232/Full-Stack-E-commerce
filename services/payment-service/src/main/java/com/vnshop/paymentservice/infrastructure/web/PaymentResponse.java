package com.vnshop.paymentservice.infrastructure.web;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentMethod;
import com.vnshop.paymentservice.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Wire shape for /payment/* endpoints. {@code redirectUrl} is the typed
 * surface for VNPay/MoMo gateway URLs — at PENDING-create time, those
 * gateways return a hosted-payment-page URL that the FE must redirect to.
 * The URL lives on {@link Payment#transactionRef()} until the IPN comes
 * back, at which point {@code transactionRef} is overwritten with the
 * gateway's transaction id. Surfacing {@code redirectUrl} as its own
 * field lets the FE consume a stable, typed shape instead of inspecting
 * {@code transactionRef} for what looks like a URL — pt41 carryover.
 *
 * <p>{@code redirectUrl} is null for COD, VietQR, Stripe, and PayPal
 * (those flows don't redirect — Stripe/PayPal mount in-page; VietQR
 * renders a QR; COD has no payment step), and null for any payment that
 * has already moved past PENDING.
 */
public record PaymentResponse(
        String paymentId,
        String orderId,
        String buyerId,
        BigDecimal amount,
        String method,
        String status,
        String transactionRef,
        String redirectUrl,
        Instant createdAt
) {
    static PaymentResponse fromDomain(Payment payment) {
        return new PaymentResponse(
                payment.paymentId().toString(),
                payment.orderId(),
                payment.buyerId(),
                payment.amount(),
                payment.method().name(),
                payment.status().name(),
                payment.transactionRef(),
                redirectUrlFor(payment),
                payment.createdAt()
        );
    }

    private static String redirectUrlFor(Payment payment) {
        if (payment.status() != PaymentStatus.PENDING) {
            return null;
        }
        if (payment.method() != PaymentMethod.VNPAY && payment.method() != PaymentMethod.MOMO) {
            return null;
        }
        String ref = payment.transactionRef();
        if (ref == null || ref.isBlank()) {
            return null;
        }
        return ref;
    }
}
