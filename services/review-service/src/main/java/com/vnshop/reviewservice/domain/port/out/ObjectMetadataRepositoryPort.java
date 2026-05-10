package com.vnshop.reviewservice.domain.port.out;

import com.vnshop.reviewservice.domain.storage.ObjectMetadata;
import java.util.Optional;

public interface ObjectMetadataRepositoryPort {
    ObjectMetadata save(ObjectMetadata metadata);

    Optional<ObjectMetadata> findByKey(String key);
}
