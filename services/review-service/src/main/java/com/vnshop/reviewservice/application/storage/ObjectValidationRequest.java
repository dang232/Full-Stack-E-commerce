package com.vnshop.reviewservice.application.storage;

import com.vnshop.reviewservice.domain.storage.ObjectMetadata;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ObjectValidationRequest {
    ObjectMetadata metadata;
    String expectedSha256Hex;
    String detectedContentType;
    boolean avScanClean;
}
