package com.vnshop.reviewservice.infrastructure.web;

import com.vnshop.reviewservice.application.CreateReviewCommand;
import com.vnshop.reviewservice.application.CreateReviewCommand;
import com.vnshop.reviewservice.application.CreateReviewUseCase;
import com.vnshop.reviewservice.application.GetProductReviewsUseCase;
import com.vnshop.reviewservice.application.VoteHelpfulUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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
    public ApiResponse<List<ReviewResponse>> byProduct(@PathVariable String productId) {
        return ApiResponse.ok(getProductReviewsUseCase.get(productId).stream().map(ReviewResponse::fromDomain).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> create(@Valid @RequestBody CreateReviewRequest request) {
        return ApiResponse.ok(ReviewResponse.fromDomain(createReviewUseCase.create(new CreateReviewCommand(request.productId(), request.buyerId(), request.orderId(), request.rating(), request.text(), request.images()))));
    }

    @PutMapping("/{id}/helpful")
    public ApiResponse<ReviewResponse> voteHelpful(@PathVariable UUID id) {
        return ApiResponse.ok(ReviewResponse.fromDomain(voteHelpfulUseCase.vote(id)));
    }
}
