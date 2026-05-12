package com.vnshop.productservice.application.review.image;

import java.net.URI;

public record ReviewImageUploadResponse(
        String objectKey,
        URI uploadUrl,
        String checksumSha256,
        String quarantineState,
        long expiresInSeconds
) {}
