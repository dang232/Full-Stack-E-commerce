package com.vnshop.productservice.application.image;

public record ProductImageActivationResponse(String objectKey, String checksumSha256, String quarantineState) {
}
