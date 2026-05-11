package com.vnshop.reviewservice.infrastructure.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReviewRequest(
        @NotBlank String productId,
        @NotBlank String buyerId,
        @NotBlank String orderId,
        @Min(1) @Max(5) int rating,
        @Size(max = 1000) String text,
        @Size(max = 5) List<String> images) {
}
