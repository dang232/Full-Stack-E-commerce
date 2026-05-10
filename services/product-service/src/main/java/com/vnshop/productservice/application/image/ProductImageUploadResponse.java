package com.vnshop.productservice.application.image;

import java.net.URI;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductImageUploadResponse {
    String objectKey;
    URI uploadUrl;
    String checksumSha256;
    String quarantineState;
    long expiresInSeconds;
}
