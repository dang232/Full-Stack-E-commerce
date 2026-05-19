package com.vnshop.paymentservice.infrastructure.web;

/**
 * Wraps the standard {@link PaymentResponse} with the VietQR-specific data
 * the FE needs to render the QR code: the image URL plus the underlying
 * bank account fields so the FE can also display them as text fallback.
 */
public record VietQrCreateResponse(
        PaymentResponse payment,
        String qrImageUrl,
        String bankBin,
        String accountNo,
        String accountName,
        String reference
) {
}
