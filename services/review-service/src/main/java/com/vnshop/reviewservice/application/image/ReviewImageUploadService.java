package com.vnshop.reviewservice.application.image;

import com.vnshop.reviewservice.application.storage.ObjectValidationRequest;
import com.vnshop.reviewservice.application.storage.ObjectValidationService;
import com.vnshop.reviewservice.domain.Review;
import com.vnshop.reviewservice.domain.port.out.ObjectMetadataRepositoryPort;
import com.vnshop.reviewservice.domain.port.out.ObjectStoragePort;
import com.vnshop.reviewservice.domain.port.out.ReviewRepositoryPort;
import com.vnshop.reviewservice.domain.storage.ObjectMetadata;
import com.vnshop.reviewservice.domain.storage.ObjectQuarantineState;
import com.vnshop.reviewservice.domain.storage.ObjectStorageClass;
import com.vnshop.reviewservice.domain.storage.ObjectValidationResult;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@RequiredArgsConstructor
public class ReviewImageUploadService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ReviewRepositoryPort reviewRepositoryPort;
    private final ObjectStoragePort objectStoragePort;
    private final ObjectMetadataRepositoryPort objectMetadataRepositoryPort;
    private final ObjectValidationService objectValidationService;

    public ReviewImageUploadResponse createUpload(ReviewImageUploadRequest request) {
        Review review = reviewRepositoryPort.findReviewById(request.getReviewId())
                .orElseThrow(() -> new IllegalArgumentException("review not found"));
        if (!review.buyerId().equals(request.getBuyerId())) {
            throw new IllegalArgumentException("review not found");
        }

        ObjectMetadata metadata = metadata(request, objectKey(request));
        ObjectValidationResult result = validate(request, metadata);
        if (!result.active()) {
            objectMetadataRepositoryPort.save(metadata.toBuilder()
                    .quarantineState(ObjectQuarantineState.REJECTED)
                    .build());
            throw new ReviewImageValidationException(result.getFailures());
        }

        ObjectMetadata pending = objectMetadataRepositoryPort.save(metadata);
        URI uploadUrl = objectStoragePort.getSignedUploadUrl(pending.getKey(), pending);
        return ReviewImageUploadResponse.builder()
                .objectKey(pending.getKey())
                .uploadUrl(uploadUrl)
                .checksumSha256(pending.getSha256Hex())
                .quarantineState(pending.getQuarantineState().name())
                .expiresInSeconds(ObjectStorageClass.REVIEW_IMAGE.uploadTtl().toSeconds())
                .build();
    }

    public ObjectMetadata activate(String objectKey, ReviewImageActivationRequest request) {
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
            throw new ReviewImageValidationException(result.getFailures());
        }
        return activated;
    }

    private ObjectValidationResult validate(ReviewImageUploadRequest request, ObjectMetadata metadata) {
        ReviewImageValidationFailures failures = new ReviewImageValidationFailures();
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

    private ObjectMetadata metadata(ReviewImageUploadRequest request, String key) {
        return ObjectMetadata.builder()
                .key(key)
                .storageClass(ObjectStorageClass.REVIEW_IMAGE)
                .contentType(request.getDetectedContentType())
                .contentLength(request.getContentLength())
                .sha256Hex(request.getSha256Hex())
                .quarantineState(ObjectQuarantineState.PENDING_VALIDATION)
                .imageWidth(request.getImageWidth())
                .imageHeight(request.getImageHeight())
                .createdAt(Instant.now())
                .build();
    }

    private String objectKey(ReviewImageUploadRequest request) {
        return "reviews/%s/images/%s.%s".formatted(request.getReviewId(), UUID.randomUUID(), extension(request.getFileName()));
    }

    private void validateExtension(String fileName, ReviewImageValidationFailures failures) {
        if (!ALLOWED_EXTENSIONS.contains(extension(fileName))) {
            failures.add("file_extension_rejected");
        }
    }

    private void validateDeclaredContentType(String declaredContentType, ReviewImageValidationFailures failures) {
        if (!ALLOWED_CONTENT_TYPES.contains(declaredContentType)) {
            failures.add("declared_content_type_rejected");
        }
    }

    private void validateChecksumShape(String sha256Hex, ReviewImageValidationFailures failures) {
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

    private static final class ReviewImageValidationFailures {
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

    @Value
    @Builder
    public static class ReviewImageActivationRequest {
        String detectedContentType;
        long contentLength;
        String sha256Hex;
        Integer imageWidth;
        Integer imageHeight;
        boolean avScanClean;
    }
}
