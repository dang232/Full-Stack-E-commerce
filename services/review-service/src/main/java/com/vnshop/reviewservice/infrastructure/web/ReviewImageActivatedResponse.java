package com.vnshop.reviewservice.infrastructure.web;

import com.vnshop.reviewservice.domain.storage.ObjectMetadata;

public record ReviewImageActivatedResponse(String objectKey, String quarantineState, String checksumSha256) {
    static ReviewImageActivatedResponse fromDomain(ObjectMetadata metadata) {
        return new ReviewImageActivatedResponse(metadata.getKey(), metadata.getQuarantineState().name(), metadata.getSha256Hex());
    }
}
