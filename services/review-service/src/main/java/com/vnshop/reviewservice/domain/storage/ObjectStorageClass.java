package com.vnshop.reviewservice.domain.storage;

import java.time.Duration;

public enum ObjectStorageClass {
    REVIEW_IMAGE(Duration.ofMinutes(15), Duration.ofMinutes(15));

    private final Duration uploadTtl;
    private final Duration downloadTtl;

    ObjectStorageClass(Duration uploadTtl, Duration downloadTtl) {
        this.uploadTtl = uploadTtl;
        this.downloadTtl = downloadTtl;
    }

    public Duration uploadTtl() {
        return uploadTtl;
    }

    public Duration downloadTtl() {
        return downloadTtl;
    }
}
