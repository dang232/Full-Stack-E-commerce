package com.vnshop.paymentservice.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Light wire shape for the payment create endpoints. Only the orderId is
 * client-supplied — buyer principal is resolved from the JWT in the
 * controller, and the authoritative payable amount is resolved server-side
 * from order-service. Closes the price-tampering finding documented in
 * docs/SESSION-HANDOVER-2026-05-20-pt12.md.
 */
public record PaymentRequest(
        @NotBlank String orderId
) {
}
