package com.vnshop.reviewservice.application;

import java.util.List;

public record CreateReviewCommand(String productId, String buyerId, String orderId, int rating, String text, List<String> images) {
}
