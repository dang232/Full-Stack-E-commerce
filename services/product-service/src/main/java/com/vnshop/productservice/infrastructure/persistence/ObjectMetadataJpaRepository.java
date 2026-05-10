package com.vnshop.productservice.infrastructure.persistence;

import com.vnshop.productservice.domain.port.out.ObjectMetadataRepositoryPort;
import com.vnshop.productservice.domain.storage.ObjectMetadata;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
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

    interface ObjectMetadataJpaSpringDataRepository extends JpaRepository<ObjectMetadataJpaEntity, String> {
    }
}
