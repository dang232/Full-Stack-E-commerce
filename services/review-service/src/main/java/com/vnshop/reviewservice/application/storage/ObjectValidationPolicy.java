package com.vnshop.reviewservice.application.storage;

import com.vnshop.reviewservice.domain.storage.ObjectStorageClass;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ObjectValidationPolicy {
    ObjectStorageClass storageClass;
    long maxBytes;
    Set<String> allowedContentTypes;
    Integer maxImageWidth;
    Integer maxImageHeight;
}
