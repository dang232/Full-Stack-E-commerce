package com.vnshop.productservice.domain.port.out;

import com.vnshop.productservice.domain.storage.ObjectMetadata;
import com.vnshop.productservice.domain.storage.ObjectStorageClass;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

public interface ObjectStoragePort {
    void putObject(String key, InputStream content, ObjectMetadata metadata);

    URI getSignedUploadUrl(String key, ObjectMetadata metadata);

    URI getSignedDownloadUrl(String key, ObjectStorageClass storageClass);

    void deleteObject(String key);

    Optional<ObjectMetadata> headObject(String key);
}
