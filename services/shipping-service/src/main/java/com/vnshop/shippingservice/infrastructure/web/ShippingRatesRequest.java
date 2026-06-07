package com.vnshop.shippingservice.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the buyer-facing {@code POST /shipping/rates} endpoint.
 * Extends the base address + parcel shape with an optional order total so the
 * service can apply the free-shipping threshold server-side and return a zero
 * fee on the standard tier when the buyer qualifies.
 */
public record ShippingRatesRequest(
        @NotBlank String street,
        String ward,
        @NotBlank String district,
        @NotBlank String province,
        String recipientName,
        String recipientPhone,
        @Valid ParcelDto parcel,
        Long orderTotalVnd) {}
