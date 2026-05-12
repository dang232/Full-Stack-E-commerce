package com.vnshop.productservice.infrastructure.web.review;

import com.vnshop.productservice.application.review.image.ReviewImageActivationRequest;
import com.vnshop.productservice.application.review.image.ReviewImageActivationResponse;
import com.vnshop.productservice.application.review.image.ReviewImageUploadRequest;
import com.vnshop.productservice.application.review.image.ReviewImageUploadResponse;
import com.vnshop.productservice.application.review.image.ReviewImageUploadService;
import com.vnshop.productservice.infrastructure.config.JwtPrincipalUtil;
import com.vnshop.productservice.infrastructure.web.ApiResponse;
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
        ReviewImageUploadResponse response = reviewImageUploadService.createUpload(new ReviewImageUploadRequest(
                reviewId,
                JwtPrincipalUtil.currentUserId(),
                request.fileName(),
                request.declaredContentType(),
                request.detectedContentType(),
                request.contentLength(),
                request.sha256Hex(),
                request.imageWidth(),
                request.imageHeight()));
        return ApiResponse.ok(ReviewImageUploadUrlResponse.fromApplication(response));
    }

    @PostMapping("/activate")
    public ApiResponse<ReviewImageActivatedResponse> activate(@Valid @RequestBody ReviewImageActivateRequest request) {
        ReviewImageActivationResponse response = reviewImageUploadService.activate(request.objectKey(), new ReviewImageActivationRequest(
                request.detectedContentType(),
                request.contentLength(),
                request.sha256Hex(),
                request.imageWidth(),
                request.imageHeight(),
                request.avScanClean()));
        return ApiResponse.ok(ReviewImageActivatedResponse.fromApplication(response));
    }

}
