package com.vnshop.productservice.application.review.image;

public record ReviewImageActivationRequest(
        String detectedContentType,
        long contentLength,
        String sha256Hex,
        Integer imageWidth,
        Integer imageHeight,
        boolean avScanClean
) {}
