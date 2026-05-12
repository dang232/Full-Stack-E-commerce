package com.vnshop.productservice.application.review;

public record AskQuestionCommand(String productId, String buyerId, String question) {
}
