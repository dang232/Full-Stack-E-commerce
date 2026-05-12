package com.vnshop.reviewservice.infrastructure.web;

import com.vnshop.reviewservice.application.image.ReviewImageActivationRequest;
import com.vnshop.reviewservice.application.image.ReviewImageActivationResponse;
import com.vnshop.reviewservice.application.image.ReviewImageUploadRequest;
import com.vnshop.reviewservice.application.image.ReviewImageUploadResponse;
import com.vnshop.reviewservice.application.image.ReviewImageUploadService;
import com.vnshop.reviewservice.infrastructure.config.JwtPrincipalUtil;
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
@RequestMapping("/reviews/{reviewId}/images")
@RequiredArgsConstructor
public class ReviewImageUploadController {
    private final ReviewImageUploadService reviewImageUploadService;

    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewImageUploadUrlResponse> createUploadUrl(@PathVariable String reviewId, @Valid @RequestBody ReviewImageUploadUrlRequest request) {
        ReviewImageUploadResponse response = reviewImageUploadService.createUpload(ReviewImageUploadRequest.builder()
                .reviewId(reviewId)
                .buyerId(JwtPrincipalUtil.currentUserId())
                .fileName(request.fileName())
                .declaredContentType(request.declaredContentType())
                .detectedContentType(request.detectedContentType())
                .contentLength(request.contentLength())
                .sha256Hex(request.sha256Hex())
                .imageWidth(request.imageWidth())
                .imageHeight(request.imageHeight())
                .build());
        return ApiResponse.ok(ReviewImageUploadUrlResponse.fromApplication(response));
    }

    @PostMapping("/activate")
    public ApiResponse<ReviewImageActivatedResponse> activate(@Valid @RequestBody ReviewImageActivateRequest request) {
        ReviewImageActivationResponse response = reviewImageUploadService.activate(request.objectKey(), ReviewImageActivationRequest.builder()
                .detectedContentType(request.detectedContentType())
                .contentLength(request.contentLength())
                .sha256Hex(request.sha256Hex())
                .imageWidth(request.imageWidth())
                .imageHeight(request.imageHeight())
                .avScanClean(request.avScanClean())
                .build());
        return ApiResponse.ok(ReviewImageActivatedResponse.fromApplication(response));
    }

}
