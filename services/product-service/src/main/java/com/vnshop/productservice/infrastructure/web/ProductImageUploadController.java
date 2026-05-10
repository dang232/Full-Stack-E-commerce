package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.application.image.ProductImageUploadRequest;
import com.vnshop.productservice.application.image.ProductImageUploadResponse;
import com.vnshop.productservice.application.image.ProductImageUploadService;
import com.vnshop.productservice.application.image.ProductImageUploadService.ProductImageActivationRequest;
import com.vnshop.productservice.domain.storage.ObjectMetadata;
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
@RequestMapping("/sellers/me/products/{productId}/images")
@RequiredArgsConstructor
public class ProductImageUploadController {
    private static final String DEFAULT_SELLER_ID = "stub-seller";

    private final ProductImageUploadService productImageUploadService;

    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadUrlResponse createUploadUrl(@RequestHeader(name = "X-Seller-Id", required = false) String sellerId,
            @PathVariable String productId, @Valid @RequestBody UploadUrlRequest request) {
        ProductImageUploadResponse response = productImageUploadService.createUpload(ProductImageUploadRequest.builder()
                .productId(productId)
                .sellerId(currentSellerId(sellerId))
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
        ObjectMetadata metadata = productImageUploadService.activate(request.objectKey(), ProductImageActivationRequest.builder()
                .detectedContentType(request.detectedContentType())
                .contentLength(request.contentLength())
                .sha256Hex(request.sha256Hex())
                .imageWidth(request.imageWidth())
                .imageHeight(request.imageHeight())
                .avScanClean(request.avScanClean())
                .build());
        return ActivatedImageResponse.fromDomain(metadata);
    }

    private static String currentSellerId(String sellerId) {
        return sellerId == null || sellerId.isBlank() ? DEFAULT_SELLER_ID : sellerId;
    }

    public record UploadUrlRequest(
            @NotBlank String fileName,
            @NotBlank String declaredContentType,
            @NotBlank String detectedContentType,
            @Min(1) long contentLength,
            @NotBlank String sha256Hex,
            @NotNull Integer imageWidth,
            @NotNull Integer imageHeight) {
    }

    public record UploadUrlResponse(String objectKey, URI uploadUrl, String checksumSha256, String quarantineState,
            long expiresInSeconds) {
        static UploadUrlResponse fromApplication(ProductImageUploadResponse response) {
            return new UploadUrlResponse(response.getObjectKey(), response.getUploadUrl(), response.getChecksumSha256(),
                    response.getQuarantineState(), response.getExpiresInSeconds());
        }
    }

    public record ActivateImageRequest(
            @NotBlank String objectKey,
            @NotBlank String detectedContentType,
            @Min(1) long contentLength,
            @NotBlank String sha256Hex,
            @NotNull Integer imageWidth,
            @NotNull Integer imageHeight,
            boolean avScanClean) {
    }

    public record ActivatedImageResponse(String objectKey, String checksumSha256, String quarantineState) {
        static ActivatedImageResponse fromDomain(ObjectMetadata metadata) {
            return new ActivatedImageResponse(metadata.getKey(), metadata.getSha256Hex(), metadata.getQuarantineState().name());
        }
    }
}
