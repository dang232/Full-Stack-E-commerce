package com.vnshop.reviewservice.application.image;

import java.net.URI;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReviewImageUploadResponse {
    String objectKey;
    URI uploadUrl;
    String checksumSha256;
    String quarantineState;
    long expiresInSeconds;
}
