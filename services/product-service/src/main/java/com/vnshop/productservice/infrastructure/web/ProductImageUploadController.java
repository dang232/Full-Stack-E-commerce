package com.vnshop.productservice.infrastructure.web;

import com.vnshop.productservice.application.image.ProductImageActivationRequest;
import com.vnshop.productservice.application.image.ProductImageActivationResponse;
import com.vnshop.productservice.application.image.ProductImageUploadRequest;
import com.vnshop.productservice.application.image.ProductImageUploadResponse;
import com.vnshop.productservice.application.image.ProductImageUploadService;
import com.vnshop.productservice.infrastructure.config.JwtPrincipalUtil;
import com.vnshop.productservice.infrastructure.web.ActivateImageRequest;
import com.vnshop.productservice.infrastructure.web.ActivatedImageResponse;
import com.vnshop.productservice.infrastructure.web.UploadUrlRequest;
import com.vnshop.productservice.infrastructure.web.UploadUrlResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sellers/me/products/{productId}/images")
@RequiredArgsConstructor
public class ProductImageUploadController {
    private final ProductImageUploadService productImageUploadService;

    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadUrlResponse> createUploadUrl(@PathVariable String productId, @Valid @RequestBody UploadUrlRequest request) {
        ProductImageUploadResponse response = productImageUploadService.createUpload(ProductImageUploadRequest.builder()
                .productId(productId)
                .sellerId(JwtPrincipalUtil.currentSellerId())
                .fileName(request.fileName())
                .declaredContentType(request.declaredContentType())
                .detectedContentType(request.detectedContentType())
                .contentLength(request.contentLength())
                .sha256Hex(request.sha256Hex())
                .imageWidth(request.imageWidth())
                .imageHeight(request.imageHeight())
                .build());
        return ApiResponse.ok(UploadUrlResponse.fromApplication(response));
    }

    @PostMapping("/activate")
    public ApiResponse<ActivatedImageResponse> activate(@Valid @RequestBody ActivateImageRequest request) {
        ProductImageActivationResponse response = productImageUploadService.activate(request.objectKey(), ProductImageActivationRequest.builder()
                .detectedContentType(request.detectedContentType())
                .contentLength(request.contentLength())
                .sha256Hex(request.sha256Hex())
                .imageWidth(request.imageWidth())
                .imageHeight(request.imageHeight())
                .avScanClean(request.avScanClean())
                .build());
        return ApiResponse.ok(ActivatedImageResponse.fromApplication(response));
    }

}
