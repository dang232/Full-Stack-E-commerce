package com.vnshop.reviewservice.infrastructure.web;

import com.vnshop.reviewservice.application.image.ReviewImageActivationResponse;

public record ReviewImageActivatedResponse(String objectKey, String quarantineState, String checksumSha256) {
    static ReviewImageActivatedResponse fromApplication(ReviewImageActivationResponse response) {
        return new ReviewImageActivatedResponse(response.objectKey(), response.quarantineState(), response.checksumSha256());
    }
}
