package com.vnshop.reviewservice.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vnshop.reviewservice.application.storage.ObjectValidationPolicy;
import com.vnshop.reviewservice.application.storage.ObjectValidationService;
import com.vnshop.reviewservice.domain.ProductQuestion;
import com.vnshop.reviewservice.domain.Review;
import com.vnshop.reviewservice.domain.ReviewStatus;
import com.vnshop.reviewservice.domain.port.out.ObjectMetadataRepositoryPort;
import com.vnshop.reviewservice.domain.port.out.ObjectStoragePort;
import com.vnshop.reviewservice.domain.port.out.ReviewRepositoryPort;
import com.vnshop.reviewservice.domain.storage.ObjectMetadata;
import com.vnshop.reviewservice.domain.storage.ObjectQuarantineState;
import com.vnshop.reviewservice.domain.storage.ObjectStorageClass;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewImageUploadServiceTest {
    private final FakeReviewRepository reviewRepository = new FakeReviewRepository();
    private final FakeObjectStorage objectStorage = new FakeObjectStorage();
    private final FakeObjectMetadataRepository metadataRepository = new FakeObjectMetadataRepository();
    private final ReviewImageUploadService service = new ReviewImageUploadService(reviewRepository, objectStorage,
            metadataRepository, new ObjectValidationService(ObjectValidationPolicy.builder()
                    .storageClass(ObjectStorageClass.REVIEW_IMAGE)
                    .maxBytes(5 * 1024 * 1024)
                    .allowedContentTypes(Set.of("image/jpeg", "image/png", "image/webp"))
                    .maxImageWidth(4096)
                    .maxImageHeight(4096)
                    .build()));

    @Test
    void createsSignedUploadUrlAndPendingMetadataForValidReviewImage() {
        reviewRepository.save(review(UUID.fromString("00000000-0000-0000-0000-000000000101"), "buyer-1"));

        ReviewImageUploadResponse response = service.createUpload(validRequest().build());

        assertThat(response.getUploadUrl()).isEqualTo(URI.create("https://storage.test/" + response.getObjectKey()));
        assertThat(response.getObjectKey()).startsWith("reviews/00000000-0000-0000-0000-000000000101/images/").endsWith(".webp");
        assertThat(response.getChecksumSha256()).isEqualTo("b".repeat(64));
        assertThat(response.getQuarantineState()).isEqualTo("PENDING_VALIDATION");
        ObjectMetadata metadata = metadataRepository.saved.get(response.getObjectKey());
        assertThat(metadata.getStorageClass()).isEqualTo(ObjectStorageClass.REVIEW_IMAGE);
        assertThat(metadata.getQuarantineState()).isEqualTo(ObjectQuarantineState.PENDING_VALIDATION);
        assertThat(metadata.getSha256Hex()).isEqualTo("b".repeat(64));
        assertThat(metadata.getContentType()).isEqualTo("image/webp");
        assertThat(objectStorage.lastMetadata).isEqualTo(metadata);
    }

    @Test
    void rejectsInvalidMetadataBeforeIssuingUploadUrl() {
        reviewRepository.save(review(UUID.fromString("00000000-0000-0000-0000-000000000101"), "buyer-1"));

        assertThatThrownBy(() -> service.createUpload(validRequest()
                .fileName("payload.exe")
                .declaredContentType("application/x-msdownload")
                .detectedContentType("application/x-msdownload")
                .contentLength(6 * 1024 * 1024)
                .sha256Hex("not-a-sha")
                .imageWidth(5000)
                .imageHeight(null)
                .build()))
                .isInstanceOf(ReviewImageValidationException.class)
                .extracting(error -> ((ReviewImageValidationException) error).failures())
                .asList()
                .contains("file_extension_rejected", "declared_content_type_rejected", "checksum_shape_invalid", "image_dimensions_required");
        assertThat(objectStorage.lastKey).isNull();
        assertThat(metadataRepository.saved.values()).singleElement()
                .extracting(ObjectMetadata::getQuarantineState)
                .isEqualTo(ObjectQuarantineState.REJECTED);
    }

    @Test
    void activatesMetadataOnlyWhenPostUploadSignalsMatch() {
        reviewRepository.save(review(UUID.fromString("00000000-0000-0000-0000-000000000101"), "buyer-1"));
        ReviewImageUploadResponse response = service.createUpload(validRequest().build());

        ObjectMetadata activated = service.activate(response.getObjectKey(), ReviewImageActivationRequest.builder()
                .detectedContentType("image/webp")
                .contentLength(2048)
                .sha256Hex("b".repeat(64))
                .imageWidth(700)
                .imageHeight(500)
                .avScanClean(true)
                .build());

        assertThat(activated.getQuarantineState()).isEqualTo(ObjectQuarantineState.ACTIVE);
        assertThat(metadataRepository.saved.get(response.getObjectKey()).getQuarantineState()).isEqualTo(ObjectQuarantineState.ACTIVE);
    }

    private ReviewImageUploadRequest.ReviewImageUploadRequestBuilder validRequest() {
        return ReviewImageUploadRequest.builder()
                .reviewId("00000000-0000-0000-0000-000000000101")
                .buyerId("buyer-1")
                .fileName("proof.webp")
                .declaredContentType("image/webp")
                .detectedContentType("image/webp")
                .contentLength(1024)
                .sha256Hex("b".repeat(64))
                .imageWidth(800)
                .imageHeight(600);
    }

    private Review review(UUID reviewId, String buyerId) {
        return new Review(reviewId, "product-1", buyerId, "order-1", 5, "Great", List.of(), true, 0,
                ReviewStatus.PENDING, Instant.now());
    }

    private static final class FakeReviewRepository implements ReviewRepositoryPort {
        private final Map<UUID, Review> reviews = new HashMap<>();

        @Override
        public Review save(Review review) {
            reviews.put(review.reviewId(), review);
            return review;
        }

        @Override
        public List<Review> findByProductId(String productId) {
            return List.of();
        }

        @Override
        public List<Review> findByBuyerId(String buyerId) {
            return List.of();
        }

        @Override
        public List<Review> findByStatus(ReviewStatus status) {
            return List.of();
        }

        @Override
        public Optional<Review> findReviewById(UUID reviewId) {
            return Optional.ofNullable(reviews.get(reviewId));
        }

        @Override
        public Review moderate(UUID reviewId, ReviewStatus status) {
            return reviews.get(reviewId).withStatus(status);
        }

        @Override
        public ProductQuestion saveQuestion(ProductQuestion question) {
            return question;
        }

        @Override
        public List<ProductQuestion> findQuestionsByProductId(String productId) {
            return List.of();
        }

        @Override
        public Optional<ProductQuestion> findQuestionById(UUID questionId) {
            return Optional.empty();
        }
    }

    private static final class FakeObjectStorage implements ObjectStoragePort {
        private String lastKey;
        private ObjectMetadata lastMetadata;

        @Override
        public void putObject(String key, InputStream content, ObjectMetadata metadata) {
        }

        @Override
        public URI getSignedUploadUrl(String key, ObjectMetadata metadata) {
            lastKey = key;
            lastMetadata = metadata;
            return URI.create("https://storage.test/" + key);
        }

        @Override
        public URI getSignedDownloadUrl(String key, ObjectStorageClass storageClass) {
            return URI.create("https://storage.test/" + key);
        }

        @Override
        public void deleteObject(String key) {
        }

        @Override
        public Optional<ObjectMetadata> headObject(String key) {
            return Optional.empty();
        }
    }

    private static final class FakeObjectMetadataRepository implements ObjectMetadataRepositoryPort {
        private final Map<String, ObjectMetadata> saved = new HashMap<>();

        @Override
        public ObjectMetadata save(ObjectMetadata metadata) {
            saved.put(metadata.getKey(), metadata);
            return metadata;
        }

        @Override
        public Optional<ObjectMetadata> findByKey(String key) {
            return Optional.ofNullable(saved.get(key));
        }
    }
}
