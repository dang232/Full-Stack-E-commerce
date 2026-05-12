package com.vnshop.reviewservice.application.image;

public record ReviewImageActivationResponse(String objectKey, String quarantineState, String checksumSha256) {
}
