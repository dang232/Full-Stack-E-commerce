package com.vnshop.orderservice.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vnshop.invoice-storage")
public class InvoiceStorageProperties {
    private boolean enabled;
    /**
     * Backend selector. R2 is the production target (S3-compatible API, zero egress
     * fees) and MINIO is the local-dev backend so devs don't need Cloudflare
     * credentials. Both speak the AWS S3 wire protocol so {@link S3InvoiceStorageAdapter}
     * doesn't care which is selected — only the endpoint, region, and path-style flag
     * differ.
     */
    private Profile profile = Profile.MINIO;
    private String bucket;
    private String region = "auto";
    private String endpoint;
    private String accessKey;
    private String secretKey;
    /**
     * R2 expects virtual-host-style addressing (false). MinIO running locally typically
     * needs path-style (true) because the bucket isn't a real DNS subdomain.
     */
    private boolean pathStyleAccess = true;

    public enum Profile {
        R2,
        MINIO
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }
}
