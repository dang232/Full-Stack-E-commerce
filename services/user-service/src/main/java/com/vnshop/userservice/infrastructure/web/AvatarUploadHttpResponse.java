package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.avatar.AvatarUploadResponse;

import java.net.URI;

public record AvatarUploadHttpResponse(
        String objectKey,
        URI uploadUrl,
        long expiresInSeconds) {
    static AvatarUploadHttpResponse fromDomain(AvatarUploadResponse response) {
        return new AvatarUploadHttpResponse(
                response.objectKey(),
                response.uploadUrl(),
                response.expiresInSeconds());
    }
}
