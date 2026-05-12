package com.vnshop.productservice.application.review;

import java.util.List;

public record CreateReviewCommand(String productId, String buyerId, String orderId, int rating, String text, List<String> images) {
}
