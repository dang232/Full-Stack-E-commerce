package com.vnshop.productservice.application.storage;

import com.vnshop.productservice.domain.storage.ObjectMetadata;
import lombok.Builder;

@Builder
public record ObjectValidationRequest(
        ObjectMetadata metadata,
        String expectedSha256Hex,
        String detectedContentType,
        boolean avScanClean
) {
}
