package com.vnshop.productservice.application.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vnshop.productservice.domain.review.SellerReviewSummary;
import com.vnshop.productservice.domain.review.port.out.ReviewRepositoryPort;
import org.junit.jupiter.api.Test;

class SellerReviewSummaryUseCaseTest {

    private final ReviewRepositoryPort reviewRepositoryPort = mock(ReviewRepositoryPort.class);
    private final SellerReviewSummaryUseCase useCase = new SellerReviewSummaryUseCase(reviewRepositoryPort);

    @Test
    void returnsSummaryFromRepository() {
        SellerReviewSummary expected = new SellerReviewSummary(4.5, 10L);
        when(reviewRepositoryPort.getSellerReviewSummary("seller-1")).thenReturn(expected);

        SellerReviewSummary result = useCase.getSummary("seller-1");

        assertThat(result.ratingAvg()).isEqualTo(4.5);
        assertThat(result.ratingCount()).isEqualTo(10L);
    }

    @Test
    void returnsNullAvgWhenNoReviews() {
        SellerReviewSummary expected = new SellerReviewSummary(null, 0L);
        when(reviewRepositoryPort.getSellerReviewSummary("seller-2")).thenReturn(expected);

        SellerReviewSummary result = useCase.getSummary("seller-2");

        assertThat(result.ratingAvg()).isNull();
        assertThat(result.ratingCount()).isZero();
    }

    @Test
    void rejectsNullRepository() {
        assertThatThrownBy(() -> new SellerReviewSummaryUseCase(null))
                .isInstanceOf(NullPointerException.class);
    }
}
