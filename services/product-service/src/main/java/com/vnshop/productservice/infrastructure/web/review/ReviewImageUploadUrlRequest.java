package com.vnshop.productservice.infrastructure.web.review;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewImageUploadUrlRequest(
        @NotBlank String fileName,
        @NotBlank String declaredContentType,
        @NotBlank String detectedContentType,
        @Min(1) long contentLength,
        @NotBlank String sha256Hex,
        @NotNull @Min(1) Integer imageWidth,
        @NotNull @Min(1) Integer imageHeight) {
}
