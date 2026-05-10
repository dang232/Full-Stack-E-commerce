package com.vnshop.reviewservice.infrastructure.web;

import com.vnshop.reviewservice.application.CreateReviewUseCase;
import com.vnshop.reviewservice.application.GetProductReviewsUseCase;
import com.vnshop.reviewservice.application.VoteHelpfulUseCase;
import com.vnshop.reviewservice.domain.Review;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/reviews")
public class ReviewController {
    private final CreateReviewUseCase createReviewUseCase;
    private final GetProductReviewsUseCase getProductReviewsUseCase;
    private final VoteHelpfulUseCase voteHelpfulUseCase;

    public ReviewController(CreateReviewUseCase createReviewUseCase, GetProductReviewsUseCase getProductReviewsUseCase,
            VoteHelpfulUseCase voteHelpfulUseCase) {
        this.createReviewUseCase = createReviewUseCase;
        this.getProductReviewsUseCase = getProductReviewsUseCase;
        this.voteHelpfulUseCase = voteHelpfulUseCase;
    }

    @GetMapping("/product/{productId}")
    public List<ReviewResponse> byProduct(@PathVariable String productId) {
        return getProductReviewsUseCase.get(productId).stream().map(ReviewResponse::fromDomain).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@Valid @RequestBody CreateReviewRequest request) {
        return ReviewResponse.fromDomain(createReviewUseCase.create(request.productId(), request.buyerId(),
                request.orderId(), request.rating(), request.text(), request.images()));
    }

    @PutMapping("/{id}/helpful")
    public ReviewResponse voteHelpful(@PathVariable String id) {
        return ReviewResponse.fromDomain(voteHelpfulUseCase.vote(id));
    }

    public record CreateReviewRequest(
            @NotBlank String productId,
            @NotBlank String buyerId,
            @NotBlank String orderId,
            @Min(1) @Max(5) int rating,
            @Size(max = 1000) String text,
            @Size(max = 5) List<String> images) {
    }

    public record ReviewResponse(String reviewId, String productId, String buyerId, String orderId, int rating,
            String text, List<String> images, boolean verifiedPurchase, int helpfulVotes, String status,
            Instant createdAt) {
        static ReviewResponse fromDomain(Review review) {
            return new ReviewResponse(review.reviewId(), review.productId(), review.buyerId(), review.orderId(),
                    review.rating(), review.text(), review.images(), review.verifiedPurchase(), review.helpfulVotes(),
                    review.status().name(), review.createdAt());
        }
    }
}
