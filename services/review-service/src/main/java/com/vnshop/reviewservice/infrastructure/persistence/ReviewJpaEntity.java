package com.vnshop.reviewservice.infrastructure.persistence;

import com.vnshop.reviewservice.domain.Review;
import com.vnshop.reviewservice.domain.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = "review_svc", name = "reviews")
public class ReviewJpaEntity {
    @Id
    @Column(name = "review_id")
    private String reviewId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "buyer_id", nullable = false)
    private String buyerId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "text", nullable = false, length = 1000)
    private String text;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "review_svc", name = "review_images", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "image_order")
    @Column(name = "image_url", nullable = false, length = 1024)
    private List<String> images = new ArrayList<>();

    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase;

    @Column(name = "helpful_votes", nullable = false)
    private int helpfulVotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReviewStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReviewJpaEntity() {
    }

    public static ReviewJpaEntity fromDomain(Review review) {
        ReviewJpaEntity entity = new ReviewJpaEntity();
        entity.reviewId = review.reviewId();
        entity.productId = review.productId();
        entity.buyerId = review.buyerId();
        entity.orderId = review.orderId();
        entity.rating = review.rating();
        entity.text = review.text();
        entity.images = new ArrayList<>(review.images());
        entity.verifiedPurchase = review.verifiedPurchase();
        entity.helpfulVotes = review.helpfulVotes();
        entity.status = review.status();
        entity.createdAt = review.createdAt();
        return entity;
    }

    public Review toDomain() {
        return new Review(reviewId, productId, buyerId, orderId, rating, text, images, verifiedPurchase,
                helpfulVotes, status, createdAt);
    }
}
