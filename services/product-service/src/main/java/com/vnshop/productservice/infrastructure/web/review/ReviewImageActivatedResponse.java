package com.vnshop.productservice.infrastructure.web.review;

import com.vnshop.productservice.application.review.image.ReviewImageActivationResponse;

public record ReviewImageActivatedResponse(String objectKey, String quarantineState, String checksumSha256) {
    static ReviewImageActivatedResponse fromApplication(ReviewImageActivationResponse response) {
        return new ReviewImageActivatedResponse(response.objectKey(), response.quarantineState(), response.checksumSha256());
    }
}
