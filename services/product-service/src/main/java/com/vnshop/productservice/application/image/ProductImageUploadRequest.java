package com.vnshop.productservice.application.image;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductImageUploadRequest {
    String productId;
    String sellerId;
    String fileName;
    String declaredContentType;
    String detectedContentType;
    long contentLength;
    String sha256Hex;
    Integer imageWidth;
    Integer imageHeight;
}
