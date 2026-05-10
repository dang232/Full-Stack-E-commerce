package com.vnshop.reviewservice.application.image;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReviewImageUploadRequest {
    String reviewId;
    String buyerId;
    String fileName;
    String declaredContentType;
    String detectedContentType;
    long contentLength;
    String sha256Hex;
    Integer imageWidth;
    Integer imageHeight;
}
