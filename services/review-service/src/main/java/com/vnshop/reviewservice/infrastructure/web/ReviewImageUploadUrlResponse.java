package com.vnshop.reviewservice.infrastructure.web;

import com.vnshop.reviewservice.application.image.ReviewImageUploadResponse;

import java.net.URI;

public record ReviewImageUploadUrlResponse(String objectKey, URI uploadUrl, String checksumSha256, String quarantineState,
        long expiresInSeconds) {
    static ReviewImageUploadUrlResponse fromApplication(ReviewImageUploadResponse response) {
        return new ReviewImageUploadUrlResponse(response.getObjectKey(), response.getUploadUrl(), response.getChecksumSha256(),
                response.getQuarantineState(), response.getExpiresInSeconds());
    }
}
