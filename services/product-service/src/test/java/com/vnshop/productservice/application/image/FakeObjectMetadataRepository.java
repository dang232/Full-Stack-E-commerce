package com.vnshop.productservice.application.image;

import com.vnshop.productservice.domain.port.out.ObjectMetadataRepositoryPort;
import com.vnshop.productservice.domain.storage.ObjectMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared in-memory fake for ObjectMetadataRepositoryPort. Pre-pt24 these
 * classes were duplicated between ProductImageUploadServiceTest and
 * ReviewImageUploadServiceTest with the same shape and the same
 * findByKeyCalls tracking list — both tests use the tracking to assert
 * the ownership gate fires before the metadata lookup.
 *
 * <p>{@link com.vnshop.productservice.application.review.image.ReviewImageUploadServiceTest}
 * imports this from a sibling package, so the class and its fields are
 * package-public. Test scope only.
 */
public final class FakeObjectMetadataRepository implements ObjectMetadataRepositoryPort {
    public final Map<String, ObjectMetadata> saved = new HashMap<>();
    public final List<String> findByKeyCalls = new ArrayList<>();

    @Override
    public ObjectMetadata save(ObjectMetadata metadata) {
        saved.put(metadata.getKey(), metadata);
        return metadata;
    }

    @Override
    public Optional<ObjectMetadata> findByKey(String key) {
        findByKeyCalls.add(key);
        return Optional.ofNullable(saved.get(key));
    }
}
