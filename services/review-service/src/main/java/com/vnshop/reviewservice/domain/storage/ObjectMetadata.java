package com.vnshop.reviewservice.domain.storage;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class ObjectMetadata {
    String key;
    ObjectStorageClass storageClass;
    String contentType;
    long contentLength;
    String sha256Hex;
    ObjectQuarantineState quarantineState;
    Integer imageWidth;
    Integer imageHeight;
    Instant createdAt;
}
