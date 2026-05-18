package com.vnshop.productservice.application.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vnshop.productservice.domain.review.SellerReviewSummary;
import com.vnshop.productservice.domain.review.port.out.ReviewRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class SellerReviewSummaryBatchUseCaseTest {

    private final ReviewRepositoryPort reviewRepositoryPort = mock(ReviewRepositoryPort.class);
    private final SellerReviewSummaryUseCase useCase = new SellerReviewSummaryUseCase(reviewRepositoryPort);

    @Test
    void happyPath_returnsMixedSummariesForThreeSellers() {
        Set<String> ids = Set.of("seller-a", "seller-b", "seller-c");
        when(reviewRepositoryPort.getSellerReviewSummaries(ids))
                .thenReturn(Map.of(
                        "seller-a", new SellerReviewSummary(4.2, 18L),
                        "seller-b", new SellerReviewSummary(null, 0L),
                        "seller-c", new SellerReviewSummary(3.8, 5L)
                ));

        Map<String, SellerReviewSummary> result = useCase.getSummaries(ids);

        assertThat(result.get("seller-a").ratingAvg()).isEqualTo(4.2);
        assertThat(result.get("seller-a").ratingCount()).isEqualTo(18L);
        assertThat(result.get("seller-b").ratingAvg()).isNull();
        assertThat(result.get("seller-b").ratingCount()).isZero();
        assertThat(result.get("seller-c").ratingAvg()).isEqualTo(3.8);
    }

    @Test
    void validation_emptySet_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> useCase.getSummaries(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        verify(reviewRepositoryPort, never()).getSellerReviewSummaries(anySet());
    }

    @Test
    void validation_moreThan100Ids_throwsIllegalArgumentException() {
        Set<String> tooMany = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "seller-" + i)
                .collect(Collectors.toSet());

        assertThatThrownBy(() -> useCase.getSummaries(tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
        verify(reviewRepositoryPort, never()).getSellerReviewSummaries(anySet());
    }

    @Test
    void validation_nullSet_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> useCase.getSummaries(null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(reviewRepositoryPort, never()).getSellerReviewSummaries(anySet());
    }
}
