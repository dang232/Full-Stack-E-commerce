package com.vnshop.userservice.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Avatar object-storage config. Separate prefix from product-service's
 * `vnshop.object-storage` so the two services can flip between MINIO and
 * R2 independently — user-service can stay on MinIO while product-service
 * runs on R2 (or vice versa) without one dragging the other along.
 *
 * publicEndpoint distinguishes the browser-facing URL from the internal
 * one: the BE signs against `endpoint` (e.g. http://minio:9000 in docker)
 * but the FE has to PUT against `publicEndpoint` (http://localhost:9000).
 * If publicEndpoint is unset, fall back to endpoint.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "vnshop.user-storage")
public class ObjectStorageProperties {
    private boolean enabled;
    private Profile profile = Profile.MINIO;
    private String bucket;
    private String region = "auto";
    private String endpoint;
    private String publicEndpoint;
    private String accessKey;
    private String secretKey;
    private boolean pathStyleAccess = true;

    public String resolvePublicEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }

    public enum Profile {
        R2,
        MINIO
    }
}
