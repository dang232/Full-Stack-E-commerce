package com.vnshop.userservice.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterSellerRequest(
        @NotBlank
        @Size(min = 2, max = 120)
        String shopName,
        @NotBlank
        @Size(min = 2, max = 120)
        String bankName,
        @NotBlank
        @Size(min = 4, max = 32)
        @Pattern(regexp = "^[0-9A-Za-z\\-]+$", message = "bankAccount must contain only digits, letters, or hyphens")
        String bankAccount
) {
}
