package com.vnshop.productservice.application.image;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductImageActivationRequest {
    String detectedContentType;
    long contentLength;
    String sha256Hex;
    Integer imageWidth;
    Integer imageHeight;
    boolean avScanClean;
}
