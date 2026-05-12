package com.vnshop.productservice.infrastructure.web.review;

import com.vnshop.productservice.infrastructure.web.ApiResponse;
import com.vnshop.productservice.application.review.ModerateReviewUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/reviews")
public class AdminReviewController {
    private final ModerateReviewUseCase moderateReviewUseCase;

    public AdminReviewController(ModerateReviewUseCase moderateReviewUseCase) {
        this.moderateReviewUseCase = moderateReviewUseCase;
    }

    @GetMapping("/pending")
    public ApiResponse<List<ReviewResponse>> pending() {
        return ApiResponse.ok(moderateReviewUseCase.pending().stream().map(ReviewResponse::fromDomain).toList());
    }

    @PutMapping("/{id}/approve")
    public ApiResponse<ReviewResponse> approve(@PathVariable UUID id) {
        return ApiResponse.ok(ReviewResponse.fromDomain(moderateReviewUseCase.approve(id)));
    }

    @PutMapping("/{id}/reject")
    public ApiResponse<ReviewResponse> reject(@PathVariable UUID id) {
        return ApiResponse.ok(ReviewResponse.fromDomain(moderateReviewUseCase.reject(id)));
    }
}
