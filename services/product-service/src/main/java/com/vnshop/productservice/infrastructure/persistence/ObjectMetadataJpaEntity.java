package com.vnshop.productservice.infrastructure.persistence;

import com.vnshop.productservice.domain.storage.ObjectMetadata;
import com.vnshop.productservice.domain.storage.ObjectQuarantineState;
import com.vnshop.productservice.domain.storage.ObjectStorageClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "product_svc", name = "object_metadata")
@Getter
@Setter
@NoArgsConstructor
public class ObjectMetadataJpaEntity {
    @Id
    @Column(name = "object_key", length = 1024)
    private String key;

    @Column(name = "storage_class", nullable = false)
    private String storageClass;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "content_length", nullable = false)
    private long contentLength;

    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String sha256Hex;

    @Column(name = "quarantine_state", nullable = false)
    private String quarantineState;

    @Column(name = "image_width")
    private Integer imageWidth;

    @Column(name = "image_height")
    private Integer imageHeight;

    @Column(name = "owner_type", nullable = false)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static ObjectMetadataJpaEntity fromDomain(ObjectMetadata metadata) {
        ObjectMetadataJpaEntity entity = new ObjectMetadataJpaEntity();
        entity.key = metadata.getKey();
        entity.storageClass = metadata.getStorageClass().name();
        entity.contentType = metadata.getContentType();
        entity.contentLength = metadata.getContentLength();
        entity.sha256Hex = metadata.getSha256Hex();
        entity.quarantineState = metadata.getQuarantineState().name();
        entity.imageWidth = metadata.getImageWidth();
        entity.imageHeight = metadata.getImageHeight();
        entity.ownerType = "PRODUCT";
        entity.ownerId = productIdFromKey(metadata.getKey());
        entity.createdAt = metadata.getCreatedAt();
        return entity;
    }

    ObjectMetadata toDomain() {
        return ObjectMetadata.builder()
                .key(key)
                .storageClass(ObjectStorageClass.valueOf(storageClass))
                .contentType(contentType)
                .contentLength(contentLength)
                .sha256Hex(sha256Hex)
                .quarantineState(ObjectQuarantineState.valueOf(quarantineState))
                .imageWidth(imageWidth)
                .imageHeight(imageHeight)
                .createdAt(createdAt)
                .build();
    }

    private static String productIdFromKey(String key) {
        String[] parts = key.split("/");
        return parts.length > 1 ? parts[1] : "unknown";
    }
}
