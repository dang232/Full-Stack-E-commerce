package com.vnshop.shippingservice.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record RateQuoteHttpRequest(
        @NotBlank String street,
        String ward,
        @NotBlank String district,
        @NotBlank String province,
        String recipientName,
        String recipientPhone,
        @Valid ParcelDto parcel) {}
