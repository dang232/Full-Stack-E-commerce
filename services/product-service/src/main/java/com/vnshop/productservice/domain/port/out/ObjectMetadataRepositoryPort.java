package com.vnshop.productservice.domain.port.out;

import com.vnshop.productservice.domain.storage.ObjectMetadata;
import java.util.Optional;

public interface ObjectMetadataRepositoryPort {
    ObjectMetadata save(ObjectMetadata metadata);

    Optional<ObjectMetadata> findByKey(String key);
}
