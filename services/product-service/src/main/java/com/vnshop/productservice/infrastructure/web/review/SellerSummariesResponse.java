package com.vnshop.productservice.infrastructure.web.review;

import com.vnshop.productservice.domain.review.SellerReviewSummary;

import java.util.Map;

public record SellerSummariesResponse(Map<String, SellerReviewSummary> summaries) {
}
