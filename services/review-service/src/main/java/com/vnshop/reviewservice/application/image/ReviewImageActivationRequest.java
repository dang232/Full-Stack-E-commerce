package com.vnshop.reviewservice.application.image;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReviewImageActivationRequest {
    String detectedContentType;
    long contentLength;
    String sha256Hex;
    Integer imageWidth;
    Integer imageHeight;
    boolean avScanClean;
}
