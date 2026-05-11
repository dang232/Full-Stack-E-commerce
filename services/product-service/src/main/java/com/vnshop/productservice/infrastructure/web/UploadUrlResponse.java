package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.application.image.ProductImageUploadResponse;
import java.net.URI;

public record UploadUrlResponse(String objectKey, URI uploadUrl, String checksumSha256, String quarantineState,
        long expiresInSeconds) {
    static UploadUrlResponse fromApplication(ProductImageUploadResponse response) {
        return new UploadUrlResponse(response.getObjectKey(), response.getUploadUrl(), response.getChecksumSha256(),
                response.getQuarantineState(), response.getExpiresInSeconds());
    }
}
