package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.domain.storage.ObjectMetadata;

public record ActivatedImageResponse(String objectKey, String checksumSha256, String quarantineState) {
    static ActivatedImageResponse fromDomain(ObjectMetadata metadata) {
        return new ActivatedImageResponse(metadata.getKey(), metadata.getSha256Hex(), metadata.getQuarantineState().name());
    }
}
