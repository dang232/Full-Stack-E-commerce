package com.vnshop.reviewservice.infrastructure.persistence;

import com.vnshop.reviewservice.domain.port.out.ObjectMetadataRepositoryPort;
import com.vnshop.reviewservice.domain.storage.ObjectMetadata;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ObjectMetadataJpaRepository implements ObjectMetadataRepositoryPort {
    private final ObjectMetadataJpaSpringDataRepository repository;

    @Override
    public ObjectMetadata save(ObjectMetadata metadata) {
        return repository.save(ObjectMetadataJpaEntity.fromDomain(metadata)).toDomain();
    }

    @Override
    public Optional<ObjectMetadata> findByKey(String key) {
        return repository.findById(key).map(ObjectMetadataJpaEntity::toDomain);
    }
}
