package com.vnshop.productservice.infrastructure.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadUrlRequest(
        @NotBlank String fileName,
        @NotBlank String declaredContentType,
        @NotBlank String detectedContentType,
        @Min(1) long contentLength,
        @NotBlank String sha256Hex,
        @NotNull Integer imageWidth,
        @NotNull Integer imageHeight) {
}
