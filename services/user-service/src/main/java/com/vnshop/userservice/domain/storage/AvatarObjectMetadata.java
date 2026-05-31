package com.vnshop.userservice.domain.storage;

/**
 * Minimal metadata required to sign a presigned PUT and verify a landed
 * object. Avatars don't need product-service's quarantine state machine
 * (one image per user, last-write-wins), so this is a simple record
 * rather than the builder-heavy ObjectMetadata pattern in product-service.
 */
public record AvatarObjectMetadata(String key, String contentType, long contentLength, String sha256Hex) {
}
