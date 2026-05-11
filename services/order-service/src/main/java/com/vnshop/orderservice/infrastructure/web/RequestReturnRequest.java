package com.vnshop.orderservice.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record RequestReturnRequest(Long subOrderId, @NotBlank String reason) {
}
