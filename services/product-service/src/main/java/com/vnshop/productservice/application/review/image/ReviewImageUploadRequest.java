package com.vnshop.productservice.application.review.image;

public record ReviewImageUploadRequest(
        String reviewId,
        String buyerId,
        String fileName,
        String declaredContentType,
        String detectedContentType,
        long contentLength,
        String sha256Hex,
        Integer imageWidth,
        Integer imageHeight
) {}
