package com.vnshop.productservice.application.image;

import lombok.Builder;

@Builder
public record ProductImageActivationRequest(
        String detectedContentType,
        long contentLength,
        String sha256Hex,
        Integer imageWidth,
        Integer imageHeight,
        boolean avScanClean
) {
}
