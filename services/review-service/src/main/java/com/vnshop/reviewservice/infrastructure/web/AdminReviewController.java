package com.vnshop.reviewservice.infrastructure.web;

import com.vnshop.reviewservice.application.ModerateReviewUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/reviews")
public class AdminReviewController {
    private final ModerateReviewUseCase moderateReviewUseCase;

    public AdminReviewController(ModerateReviewUseCase moderateReviewUseCase) {
        this.moderateReviewUseCase = moderateReviewUseCase;
    }

    @GetMapping("/pending")
    public List<ReviewController.ReviewResponse> pending() {
        return moderateReviewUseCase.pending().stream().map(ReviewController.ReviewResponse::fromDomain).toList();
    }

    @PutMapping("/{id}/approve")
    public ReviewController.ReviewResponse approve(@PathVariable String id) {
        return ReviewController.ReviewResponse.fromDomain(moderateReviewUseCase.approve(id));
    }

    @PutMapping("/{id}/reject")
    public ReviewController.ReviewResponse reject(@PathVariable String id) {
        return ReviewController.ReviewResponse.fromDomain(moderateReviewUseCase.reject(id));
    }
}
