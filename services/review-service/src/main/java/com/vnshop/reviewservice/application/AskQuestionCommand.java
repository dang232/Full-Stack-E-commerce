package com.vnshop.reviewservice.application;

public record AskQuestionCommand(String productId, String buyerId, String question) {
}
