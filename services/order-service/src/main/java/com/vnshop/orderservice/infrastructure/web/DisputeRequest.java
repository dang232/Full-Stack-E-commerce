package com.vnshop.orderservice.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record DisputeRequest(@NotBlank String buyerReason, String sellerResponse) {
}
