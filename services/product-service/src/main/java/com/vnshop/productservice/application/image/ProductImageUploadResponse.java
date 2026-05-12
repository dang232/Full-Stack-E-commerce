package com.vnshop.productservice.application.image;

import java.net.URI;
import lombok.Builder;

@Builder
public record ProductImageUploadResponse(
        String objectKey,
        URI uploadUrl,
        String checksumSha256,
        String quarantineState,
        long expiresInSeconds
) {
}
