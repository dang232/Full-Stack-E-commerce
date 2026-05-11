package com.vnshop.orderservice.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record ShipRequest(@NotBlank String carrier, @NotBlank String trackingNumber) {
}
