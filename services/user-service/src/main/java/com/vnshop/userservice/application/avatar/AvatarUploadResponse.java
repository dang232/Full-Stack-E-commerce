package com.vnshop.userservice.application.avatar;

import java.net.URI;

public record AvatarUploadResponse(
        String objectKey,
        URI uploadUrl,
        long expiresInSeconds
) {
}
