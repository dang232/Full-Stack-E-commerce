package com.vnshop.productservice.application.image;

import com.vnshop.productservice.application.storage.ObjectValidationPolicy;
import com.vnshop.productservice.application.storage.ObjectValidationRequest;
import com.vnshop.productservice.application.storage.ObjectValidationService;
import com.vnshop.productservice.domain.Product;
import com.vnshop.productservice.domain.port.out.ObjectMetadataRepositoryPort;
import com.vnshop.productservice.domain.port.out.ObjectStoragePort;
import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;
import com.vnshop.productservice.domain.storage.ObjectMetadata;
import com.vnshop.productservice.domain.storage.ObjectQuarantineState;
import com.vnshop.productservice.domain.storage.ObjectStorageClass;
import com.vnshop.productservice.domain.storage.ObjectValidationResult;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductImageUploadService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGE_WIDTH = 4096;
    private static final int MAX_IMAGE_HEIGHT = 4096;

    private final ProductRepositoryPort productRepositoryPort;
    private final ObjectStoragePort objectStoragePort;
    private final ObjectMetadataRepositoryPort objectMetadataRepositoryPort;
    private final ObjectValidationService objectValidationService;

    public ProductImageUploadResponse createUpload(ProductImageUploadRequest request) {
        Product product = productRepositoryPort.findById(UUID.fromString(request.getProductId()))
                .orElseThrow(() -> new IllegalArgumentException("product not found"));
        if (!product.sellerId().equals(request.getSellerId())) {
            throw new IllegalArgumentException("product not found");
        }

        ObjectMetadata metadata = metadata(request, objectKey(request));
        ObjectValidationResult result = validate(request, metadata);
        if (!result.active()) {
            objectMetadataRepositoryPort.save(metadata.toBuilder()
                    .quarantineState(ObjectQuarantineState.REJECTED)
                    .build());
            throw new ProductImageValidationException(result.getFailures());
        }

        ObjectMetadata pending = objectMetadataRepositoryPort.save(metadata);
        URI uploadUrl = objectStoragePort.getSignedUploadUrl(pending.getKey(), pending);
        return ProductImageUploadResponse.builder()
                .objectKey(pending.getKey())
                .uploadUrl(uploadUrl)
                .checksumSha256(pending.getSha256Hex())
                .quarantineState(pending.getQuarantineState().name())
                .expiresInSeconds(ObjectStorageClass.PRODUCT_IMAGE.uploadTtl().toSeconds())
                .build();
    }

    public ObjectMetadata activate(String objectKey, ProductImageActivationRequest request) {
        ObjectMetadata metadata = objectMetadataRepositoryPort.findByKey(objectKey)
                .orElseThrow(() -> new IllegalArgumentException("object metadata not found"));
        ObjectValidationResult result = objectValidationService.validate(ObjectValidationRequest.builder()
                .metadata(metadata.toBuilder()
                        .contentLength(request.getContentLength())
                        .sha256Hex(request.getSha256Hex())
                        .imageWidth(request.getImageWidth())
                        .imageHeight(request.getImageHeight())
                        .build())
                .expectedSha256Hex(metadata.getSha256Hex())
                .detectedContentType(request.getDetectedContentType())
                .avScanClean(request.isAvScanClean())
                .build());
        ObjectMetadata activated = metadata.toBuilder()
                .contentLength(request.getContentLength())
                .sha256Hex(request.getSha256Hex())
                .imageWidth(request.getImageWidth())
                .imageHeight(request.getImageHeight())
                .quarantineState(result.active() ? ObjectQuarantineState.ACTIVE : ObjectQuarantineState.REJECTED)
                .build();
        objectMetadataRepositoryPort.save(activated);
        if (!result.active()) {
            throw new ProductImageValidationException(result.getFailures());
        }
        return activated;
    }

    private ObjectValidationResult validate(ProductImageUploadRequest request, ObjectMetadata metadata) {
        ProductImageValidationFailures failures = new ProductImageValidationFailures();
        validateExtension(request.getFileName(), failures);
        validateDeclaredContentType(request.getDeclaredContentType(), failures);
        validateChecksumShape(request.getSha256Hex(), failures);
        if (request.getImageWidth() == null || request.getImageHeight() == null) {
            failures.add("image_dimensions_required");
        }
        if (!failures.empty()) {
            return failures.toResult();
        }
        return objectValidationService.validate(ObjectValidationRequest.builder()
                .metadata(metadata)
                .expectedSha256Hex(request.getSha256Hex())
                .detectedContentType(request.getDetectedContentType())
                .avScanClean(true)
                .build());
    }

    private ObjectMetadata metadata(ProductImageUploadRequest request, String key) {
        return ObjectMetadata.builder()
                .key(key)
                .storageClass(ObjectStorageClass.PRODUCT_IMAGE)
                .contentType(request.getDetectedContentType())
                .contentLength(request.getContentLength())
                .sha256Hex(request.getSha256Hex())
                .quarantineState(ObjectQuarantineState.PENDING_VALIDATION)
                .imageWidth(request.getImageWidth())
                .imageHeight(request.getImageHeight())
                .createdAt(Instant.now())
                .build();
    }

    private String objectKey(ProductImageUploadRequest request) {
        return "products/%s/images/%s.%s".formatted(request.getProductId(), UUID.randomUUID(), extension(request.getFileName()));
    }

    private void validateExtension(String fileName, ProductImageValidationFailures failures) {
        if (!ALLOWED_EXTENSIONS.contains(extension(fileName))) {
            failures.add("file_extension_rejected");
        }
    }

    private void validateDeclaredContentType(String declaredContentType, ProductImageValidationFailures failures) {
        if (!ALLOWED_CONTENT_TYPES.contains(declaredContentType)) {
            failures.add("declared_content_type_rejected");
        }
    }

    private void validateChecksumShape(String sha256Hex, ProductImageValidationFailures failures) {
        if (sha256Hex == null || !sha256Hex.matches("[a-fA-F0-9]{64}")) {
            failures.add("checksum_shape_invalid");
        }
    }

    private String extension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static final class ProductImageValidationFailures {
        private final java.util.List<String> values = new java.util.ArrayList<>();

        void add(String failure) {
            values.add(failure);
        }

        boolean empty() {
            return values.isEmpty();
        }

        ObjectValidationResult toResult() {
            return ObjectValidationResult.builder()
                    .quarantineState(ObjectQuarantineState.REJECTED)
                    .failures(java.util.List.copyOf(values))
                    .build();
        }
    }

}
