package com.vnshop.userservice.application.avatar;

public record AvatarUploadRequest(
        String filename,
        String contentType,
        long contentLength,
        String sha256Hex
) {
}
