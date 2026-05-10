package com.vnshop.productservice.application.storage;

import com.vnshop.productservice.domain.storage.ObjectQuarantineState;
import com.vnshop.productservice.domain.storage.ObjectValidationResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ObjectValidationService {
    private final ObjectValidationPolicy policy;

    public ObjectValidationResult validate(ObjectValidationRequest request) {
        List<String> failures = new ArrayList<>();
        validateChecksum(request, failures);
        validateMimeMagicBytes(request, failures);
        validateAvHook(request, failures);
        validateSize(request, failures);
        validateImageDimensions(request, failures);
        return ObjectValidationResult.builder()
                .quarantineState(failures.isEmpty() ? ObjectQuarantineState.ACTIVE : ObjectQuarantineState.REJECTED)
                .failures(List.copyOf(failures))
                .build();
    }

    private void validateChecksum(ObjectValidationRequest request, List<String> failures) {
        if (request.getExpectedSha256Hex() == null || !request.getExpectedSha256Hex().equalsIgnoreCase(request.getMetadata().getSha256Hex())) {
            failures.add("checksum_mismatch");
        }
    }

    private void validateMimeMagicBytes(ObjectValidationRequest request, List<String> failures) {
        if (!policy.getAllowedContentTypes().contains(request.getDetectedContentType())) {
            failures.add("mime_magic_bytes_rejected");
        }
    }

    private void validateAvHook(ObjectValidationRequest request, List<String> failures) {
        if (!request.isAvScanClean()) {
            failures.add("av_scan_required_or_failed");
        }
    }

    private void validateSize(ObjectValidationRequest request, List<String> failures) {
        if (request.getMetadata().getContentLength() > policy.getMaxBytes()) {
            failures.add("object_too_large");
        }
    }

    private void validateImageDimensions(ObjectValidationRequest request, List<String> failures) {
        if (policy.getMaxImageWidth() != null && request.getMetadata().getImageWidth() != null && request.getMetadata().getImageWidth() > policy.getMaxImageWidth()) {
            failures.add("image_width_too_large");
        }
        if (policy.getMaxImageHeight() != null && request.getMetadata().getImageHeight() != null && request.getMetadata().getImageHeight() > policy.getMaxImageHeight()) {
            failures.add("image_height_too_large");
        }
    }
}
