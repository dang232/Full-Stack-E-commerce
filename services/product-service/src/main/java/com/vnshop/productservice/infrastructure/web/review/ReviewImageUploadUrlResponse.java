package com.vnshop.productservice.infrastructure.web.review;

import com.vnshop.productservice.application.review.image.ReviewImageUploadResponse;

import java.net.URI;

public record ReviewImageUploadUrlResponse(String objectKey, URI uploadUrl, String checksumSha256, String quarantineState,
        long expiresInSeconds) {
    static ReviewImageUploadUrlResponse fromApplication(ReviewImageUploadResponse response) {
        return new ReviewImageUploadUrlResponse(response.objectKey(), response.uploadUrl(), response.checksumSha256(),
                response.quarantineState(), response.expiresInSeconds());
    }
}
