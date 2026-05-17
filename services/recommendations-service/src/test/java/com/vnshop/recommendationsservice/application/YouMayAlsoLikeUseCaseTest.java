package com.vnshop.recommendationsservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class YouMayAlsoLikeUseCaseTest {

    @Test
    void filtersToSameCategoryWithinPriceBandSortedByPopularity() {
        ProductProjection source = projection("source", "phones", new BigDecimal("1000"), 0, 0.0);
        ProductProjection inBand1 = projection("a", "phones", new BigDecimal("900"), 100, 4.5);
        ProductProjection inBand2 = projection("b", "phones", new BigDecimal("1200"), 50, 4.8);
        ProductProjection outOfBand = projection("c", "phones", new BigDecimal("2000"), 999, 5.0);
        ProductProjection wrongCategory = projection("d", "laptops", new BigDecimal("1100"), 200, 4.0);
        StubProductPort port = new StubProductPort(
                Map.of("source", source),
                Map.of(
                        "phones", List.of(source, inBand1, inBand2, outOfBand),
                        "laptops", List.of(wrongCategory)));

        YouMayAlsoLikeUseCase useCase = new YouMayAlsoLikeUseCase(port, 30, 100);

        List<ProductProjection> result = useCase.findFor("source", 8);

        assertThat(result).extracting(ProductProjection::id).containsExactly("a", "b");
    }

    @Test
    void excludesSourceProductFromCandidates() {
        ProductProjection source = projection("source", "phones", new BigDecimal("1000"), 0, 0.0);
        ProductProjection other = projection("a", "phones", new BigDecimal("950"), 50, 4.0);
        StubProductPort port = new StubProductPort(
                Map.of("source", source),
                Map.of("phones", List.of(source, other)));

        YouMayAlsoLikeUseCase useCase = new YouMayAlsoLikeUseCase(port, 30, 100);

        assertThat(useCase.findFor("source", 8))
                .extracting(ProductProjection::id).containsExactly("a");
    }

    @Test
    void emptyWhenSourceProductMissing() {
        StubProductPort port = new StubProductPort(Map.of(), Map.of());

        YouMayAlsoLikeUseCase useCase = new YouMayAlsoLikeUseCase(port, 30, 100);

        assertThat(useCase.findFor("missing", 8)).isEmpty();
    }

    @Test
    void emptyWhenSourceHasNoCategory() {
        ProductProjection orphaned = projection("source", null, new BigDecimal("1000"), 0, 0.0);
        StubProductPort port = new StubProductPort(Map.of("source", orphaned), Map.of());

        YouMayAlsoLikeUseCase useCase = new YouMayAlsoLikeUseCase(port, 30, 100);

        assertThat(useCase.findFor("source", 8)).isEmpty();
    }

    @Test
    void zeroPriceSourceFallsBackToWholeCategory() {
        ProductProjection source = projection("source", "phones", BigDecimal.ZERO, 0, 0.0);
        ProductProjection other = projection("a", "phones", new BigDecimal("99999"), 50, 4.0);
        StubProductPort port = new StubProductPort(
                Map.of("source", source),
                Map.of("phones", List.of(source, other)));

        YouMayAlsoLikeUseCase useCase = new YouMayAlsoLikeUseCase(port, 30, 100);

        assertThat(useCase.findFor("source", 8))
                .extracting(ProductProjection::id).containsExactly("a");
    }

    @Test
    void rankingOrdersByQuantitySoldThenRating() {
        ProductProjection source = projection("source", "phones", new BigDecimal("1000"), 0, 0.0);
        ProductProjection a = projection("a", "phones", new BigDecimal("950"), 50, 4.5);
        ProductProjection b = projection("b", "phones", new BigDecimal("1050"), 50, 4.9);
        ProductProjection c = projection("c", "phones", new BigDecimal("950"), 100, 3.0);
        StubProductPort port = new StubProductPort(
                Map.of("source", source),
                Map.of("phones", List.of(source, a, b, c)));

        YouMayAlsoLikeUseCase useCase = new YouMayAlsoLikeUseCase(port, 30, 100);

        assertThat(useCase.findFor("source", 8))
                .extracting(ProductProjection::id).containsExactly("c", "b", "a");
    }

    @Test
    void rejectsInvalidConstructorArgs() {
        StubProductPort port = new StubProductPort(Map.of(), Map.of());

        assertThatThrownBy(() -> new YouMayAlsoLikeUseCase(port, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new YouMayAlsoLikeUseCase(port, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new YouMayAlsoLikeUseCase(port, 30, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankProductId() {
        StubProductPort port = new StubProductPort(Map.of(), Map.of());

        YouMayAlsoLikeUseCase useCase = new YouMayAlsoLikeUseCase(port, 30, 100);

        assertThatThrownBy(() -> useCase.findFor(" ", 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.findFor(null, 8)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroLimitReturnsEmpty() {
        ProductProjection source = projection("source", "phones", new BigDecimal("1000"), 0, 0.0);
        StubProductPort port = new StubProductPort(
                Map.of("source", source),
                Map.of("phones", List.of(source, projection("a", "phones", new BigDecimal("950"), 50, 4.5))));

        YouMayAlsoLikeUseCase useCase = new YouMayAlsoLikeUseCase(port, 30, 100);

        assertThat(useCase.findFor("source", 0)).isEmpty();
    }

    private static ProductProjection projection(
            String id, String category, BigDecimal price, int sold, double rating) {
        return new ProductProjection(id, "seller", "name-" + id, category, "img", price, null, 0, rating, sold, List.of());
    }

    private static final class StubProductPort implements ProductServicePort {
        private final Map<String, ProductProjection> byId;
        private final Map<String, List<ProductProjection>> byCategory;

        StubProductPort(
                Map<String, ProductProjection> byId,
                Map<String, List<ProductProjection>> byCategory) {
            this.byId = new HashMap<>(byId);
            this.byCategory = new HashMap<>(byCategory);
        }

        @Override
        public Optional<ProductProjection> findById(String productId) {
            return Optional.ofNullable(byId.get(productId));
        }

        @Override
        public List<ProductProjection> listByCategory(String categoryId, int limit) {
            return byCategory.getOrDefault(categoryId, List.of()).stream()
                    .limit(limit)
                    .toList();
        }
    }
}
