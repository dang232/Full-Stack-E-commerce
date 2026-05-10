package com.vnshop.reviewservice.infrastructure.web;

import com.vnshop.reviewservice.application.image.ReviewImageUploadRequest;
import com.vnshop.reviewservice.application.image.ReviewImageUploadResponse;
import com.vnshop.reviewservice.application.image.ReviewImageUploadService;
import com.vnshop.reviewservice.application.image.ReviewImageUploadService.ReviewImageActivationRequest;
import com.vnshop.reviewservice.domain.storage.ObjectMetadata;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reviews/{reviewId}/images")
@RequiredArgsConstructor
public class ReviewImageUploadController {
    private static final String DEFAULT_BUYER_ID = "stub-buyer";

    private final ReviewImageUploadService reviewImageUploadService;

    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadUrlResponse createUploadUrl(@RequestHeader(name = "X-Buyer-Id", required = false) String buyerId,
            @PathVariable String reviewId, @Valid @RequestBody UploadUrlRequest request) {
        ReviewImageUploadResponse response = reviewImageUploadService.createUpload(ReviewImageUploadRequest.builder()
                .reviewId(reviewId)
                .buyerId(currentBuyerId(buyerId))
                .fileName(request.fileName())
                .declaredContentType(request.declaredContentType())
                .detectedContentType(request.detectedContentType())
                .contentLength(request.contentLength())
                .sha256Hex(request.sha256Hex())
                .imageWidth(request.imageWidth())
                .imageHeight(request.imageHeight())
                .build());
        return UploadUrlResponse.fromApplication(response);
    }

    @PostMapping("/activate")
    public ActivatedImageResponse activate(@Valid @RequestBody ActivateImageRequest request) {
        ObjectMetadata metadata = reviewImageUploadService.activate(request.objectKey(), ReviewImageActivationRequest.builder()
                .detectedContentType(request.detectedContentType())
                .contentLength(request.contentLength())
                .sha256Hex(request.sha256Hex())
                .imageWidth(request.imageWidth())
                .imageHeight(request.imageHeight())
                .avScanClean(request.avScanClean())
                .build());
        return ActivatedImageResponse.fromDomain(metadata);
    }

    private String currentBuyerId(String buyerId) {
        return buyerId == null || buyerId.isBlank() ? DEFAULT_BUYER_ID : buyerId;
    }

    public record UploadUrlRequest(
            @NotBlank String fileName,
            @NotBlank String declaredContentType,
            @NotBlank String detectedContentType,
            @Min(1) long contentLength,
            @NotBlank String sha256Hex,
            @NotNull @Min(1) Integer imageWidth,
            @NotNull @Min(1) Integer imageHeight) {
    }

    public record UploadUrlResponse(String objectKey, URI uploadUrl, String checksumSha256, String quarantineState,
            long expiresInSeconds) {
        static UploadUrlResponse fromApplication(ReviewImageUploadResponse response) {
            return new UploadUrlResponse(response.getObjectKey(), response.getUploadUrl(), response.getChecksumSha256(),
                    response.getQuarantineState(), response.getExpiresInSeconds());
        }
    }

    public record ActivateImageRequest(
            @NotBlank String objectKey,
            @NotBlank String detectedContentType,
            @Min(1) long contentLength,
            @NotBlank String sha256Hex,
            @NotNull @Min(1) Integer imageWidth,
            @NotNull @Min(1) Integer imageHeight,
            boolean avScanClean) {
    }

    public record ActivatedImageResponse(String objectKey, String quarantineState, String checksumSha256) {
        static ActivatedImageResponse fromDomain(ObjectMetadata metadata) {
            return new ActivatedImageResponse(metadata.getKey(), metadata.getQuarantineState().name(), metadata.getSha256Hex());
        }
    }
}
