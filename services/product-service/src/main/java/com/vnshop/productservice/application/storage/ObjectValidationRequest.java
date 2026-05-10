package com.vnshop.productservice.application.storage;

import com.vnshop.productservice.domain.storage.ObjectMetadata;
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
