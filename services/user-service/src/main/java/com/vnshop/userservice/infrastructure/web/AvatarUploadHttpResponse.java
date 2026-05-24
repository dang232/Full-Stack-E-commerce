package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.avatar.AvatarActivationRequest;
import com.vnshop.userservice.application.avatar.AvatarActivationResponse;
import com.vnshop.userservice.application.avatar.AvatarUploadRequest;
import com.vnshop.userservice.application.avatar.AvatarUploadResponse;

import java.net.URI;
import lombok.Builder;

@Builder
public record AvatarUploadHttpResponse(
        String objectKey,
        URI uploadUrl,
        long expiresInSeconds) {
    static AvatarUploadHttpResponse fromDomain(AvatarUploadResponse response) {
        return AvatarUploadHttpResponse.builder()
                .objectKey(response.objectKey())
                .uploadUrl(response.uploadUrl())
                .expiresInSeconds(response.expiresInSeconds())
                .build();
    }
}
