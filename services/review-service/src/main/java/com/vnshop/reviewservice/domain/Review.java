package com.vnshop.reviewservice.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Review {
    private final UUID reviewId;
    private final String productId;
    private final String buyerId;
    private final String orderId;
    private final int rating;
    private final String text;
    private final List<String> images;
    private final boolean verifiedPurchase;
    private final int helpfulVotes;
    private final ReviewStatus status;
    private final Instant createdAt;

    public Review(UUID reviewId, String productId, String buyerId, String orderId, int rating, String text,
            List<String> images, boolean verifiedPurchase, int helpfulVotes, ReviewStatus status, Instant createdAt) {
        this.reviewId = Objects.requireNonNull(reviewId, "reviewId is required");
        this.productId = requireNonBlank(productId, "productId");
        this.buyerId = requireNonBlank(buyerId, "buyerId");
        this.orderId = requireNonBlank(orderId, "orderId");
        this.rating = requireRating(rating);
        this.text = requireMaxLength(text, "text", 1000);
        this.images = List.copyOf(requireImageLimit(images));
        this.verifiedPurchase = verifiedPurchase;
        this.helpfulVotes = requireNonNegative(helpfulVotes, "helpfulVotes");
        this.status = Objects.requireNonNull(status, "status is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static Review pending(String productId, String buyerId, String orderId, int rating, String text,
            List<String> images, boolean verifiedPurchase) {
        return new Review(UUID.randomUUID(), productId, buyerId, orderId, rating, text, images,
                verifiedPurchase, 0, ReviewStatus.PENDING, Instant.now());
    }

    public Review withStatus(ReviewStatus nextStatus) {
        return new Review(reviewId, productId, buyerId, orderId, rating, text, images, verifiedPurchase,
                helpfulVotes, nextStatus, createdAt);
    }

    public Review withHelpfulVote() {
        return new Review(reviewId, productId, buyerId, orderId, rating, text, images, verifiedPurchase,
                helpfulVotes + 1, status, createdAt);
    }

    public UUID reviewId() {
        return reviewId;
    }

    public String productId() {
        return productId;
    }

    public String buyerId() {
        return buyerId;
    }

    public String orderId() {
        return orderId;
    }

    public int rating() {
        return rating;
    }

    public String text() {
        return text;
    }

    public List<String> images() {
        return images;
    }

    public boolean verifiedPurchase() {
        return verifiedPurchase;
    }

    public int helpfulVotes() {
        return helpfulVotes;
    }

    public ReviewStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static int requireRating(int value) {
        if (value < 1 || value > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        return value;
    }

    private static String requireMaxLength(String value, String fieldName, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    private static List<String> requireImageLimit(List<String> values) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > 5) {
            throw new IllegalArgumentException("images must contain at most 5 URLs");
        }
        return values;
    }

    private static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }
}
