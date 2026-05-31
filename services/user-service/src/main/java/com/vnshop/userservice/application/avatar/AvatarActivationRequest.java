package com.vnshop.userservice.application.avatar;

public record AvatarActivationRequest(
        String objectKey,
        long contentLength,
        String sha256Hex
) {
}
