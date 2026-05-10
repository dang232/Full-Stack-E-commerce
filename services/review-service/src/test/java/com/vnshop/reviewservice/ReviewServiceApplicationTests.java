package com.vnshop.reviewservice;

import com.vnshop.reviewservice.domain.Review;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewServiceApplicationTests {

    @Test
    void createsPendingVerifiedReview() {
        Review review = Review.pending("product-1", "buyer-1", "order-1", 5, "Great", List.of("https://img.example/1.jpg"), true);

        assertEquals("product-1", review.productId());
        assertEquals(5, review.rating());
        assertTrue(review.verifiedPurchase());
        assertEquals(0, review.helpfulVotes());
    }

    @Test
    void rejectsRatingOutsideOneToFive() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Review.pending("product-1", "buyer-1", "order-1", 6, "Great", List.of(), true));

        assertEquals("rating must be between 1 and 5", exception.getMessage());
    }

    @Test
    void rejectsMoreThanFiveImages() {
        List<String> images = List.of("1", "2", "3", "4", "5", "6");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Review.pending("product-1", "buyer-1", "order-1", 5, "Great", images, true));

        assertEquals("images must contain at most 5 URLs", exception.getMessage());
    }
}
