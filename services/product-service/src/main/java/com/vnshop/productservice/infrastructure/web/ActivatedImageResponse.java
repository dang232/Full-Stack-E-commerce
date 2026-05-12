package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.application.image.ProductImageActivationResponse;

public record ActivatedImageResponse(String objectKey, String checksumSha256, String quarantineState) {
    static ActivatedImageResponse fromApplication(ProductImageActivationResponse response) {
        return new ActivatedImageResponse(response.objectKey(), response.checksumSha256(), response.quarantineState());
    }
}
