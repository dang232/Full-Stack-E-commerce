package com.vnshop.shippingservice.infrastructure.web;

import jakarta.validation.constraints.NotNull;

public record ParcelDto(
        @NotNull Integer weightGrams,
        @NotNull Integer lengthCm,
        @NotNull Integer widthCm,
        @NotNull Integer heightCm) {}
