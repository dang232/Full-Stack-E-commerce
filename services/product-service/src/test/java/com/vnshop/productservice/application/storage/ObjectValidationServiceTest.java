package com.vnshop.productservice.application.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.vnshop.productservice.domain.storage.ObjectMetadata;
import com.vnshop.productservice.domain.storage.ObjectQuarantineState;
import com.vnshop.productservice.domain.storage.ObjectStorageClass;
import com.vnshop.productservice.domain.storage.ObjectValidationResult;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ObjectValidationServiceTest {
    private final ObjectValidationService service = new ObjectValidationService(ObjectValidationPolicy.builder()
            .storageClass(ObjectStorageClass.PRODUCT_IMAGE)
            .maxBytes(1024)
            .allowedContentTypes(Set.of("image/png"))
            .maxImageWidth(800)
            .maxImageHeight(600)
            .build());

    @Test
    void activatesObjectWhenAllValidationHooksPass() {
        ObjectValidationResult result = service.validate(ObjectValidationRequest.builder()
                .metadata(metadata("abc123", 512, 640, 480))
                .expectedSha256Hex("ABC123")
                .detectedContentType("image/png")
                .avScanClean(true)
                .build());

        assertThat(result.getQuarantineState()).isEqualTo(ObjectQuarantineState.ACTIVE);
        assertThat(result.active()).isTrue();
        assertThat(result.getFailures()).isEmpty();
    }

    @Test
    void rejectsObjectWhenChecksumMimeAvSizeAndDimensionsFail() {
        ObjectValidationResult result = service.validate(ObjectValidationRequest.builder()
                .metadata(metadata("abc123", 2048, 1200, 900))
                .expectedSha256Hex("different")
                .detectedContentType("application/pdf")
                .avScanClean(false)
                .build());

        assertThat(result.getQuarantineState()).isEqualTo(ObjectQuarantineState.REJECTED);
        assertThat(result.getFailures()).containsExactly(
                "checksum_mismatch",
                "mime_magic_bytes_rejected",
                "av_scan_required_or_failed",
                "object_too_large",
                "image_width_too_large",
                "image_height_too_large");
    }

    private ObjectMetadata metadata(String sha256Hex, long contentLength, Integer width, Integer height) {
        return ObjectMetadata.builder()
                .key("products/p1/main.png")
                .storageClass(ObjectStorageClass.PRODUCT_IMAGE)
                .contentType("image/png")
                .contentLength(contentLength)
                .sha256Hex(sha256Hex)
                .quarantineState(ObjectQuarantineState.PENDING_VALIDATION)
                .imageWidth(width)
                .imageHeight(height)
                .createdAt(Instant.parse("2026-05-10T00:00:00Z"))
                .build();
    }
}
