package com.vnshop.recommendationsservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vnshop.recommendationsservice.infrastructure.persistence.CoPurchaseJpaEntity;
import com.vnshop.recommendationsservice.infrastructure.persistence.CoPurchaseRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class FrequentlyBoughtTogetherUseCaseTest {

    @Test
    void returnsTopCoPurchasedProductsEnrichedFromProductService() {
        CoPurchaseRepository repo = mock(CoPurchaseRepository.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(repo.findTopByProductA(eq("source"), pageableCaptor.capture())).thenReturn(List.of(
                row("source", "p-1", 10),
                row("source", "p-2", 5),
                row("source", "p-3", 1)
        ));
        StubProductPort products = new StubProductPort(Map.of(
                "p-1", projection("p-1"),
                "p-2", projection("p-2"),
                "p-3", projection("p-3")
        ));

        FrequentlyBoughtTogetherUseCase useCase = new FrequentlyBoughtTogetherUseCase(repo, products);

        List<ProductProjection> result = useCase.findFor("source", 4);

        assertThat(result).extracting(ProductProjection::id).containsExactly("p-1", "p-2", "p-3");
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(4);
    }

    @Test
    void dropsRowsWhereProductLookupMisses() {
        CoPurchaseRepository repo = mock(CoPurchaseRepository.class);
        when(repo.findTopByProductA(eq("source"), any())).thenReturn(List.of(
                row("source", "p-1", 10),
                row("source", "deleted", 5)
        ));
        StubProductPort products = new StubProductPort(Map.of("p-1", projection("p-1")));

        FrequentlyBoughtTogetherUseCase useCase = new FrequentlyBoughtTogetherUseCase(repo, products);

        List<ProductProjection> result = useCase.findFor("source", 4);

        assertThat(result).extracting(ProductProjection::id).containsExactly("p-1");
    }

    @Test
    void emptyResultWhenNoCoPurchaseRowsExist() {
        CoPurchaseRepository repo = mock(CoPurchaseRepository.class);
        when(repo.findTopByProductA(eq("source"), any())).thenReturn(List.of());

        FrequentlyBoughtTogetherUseCase useCase = new FrequentlyBoughtTogetherUseCase(repo, new StubProductPort(Map.of()));

        // Source product unknown → no category to fall back on → still empty.
        assertThat(useCase.findFor("source", 4)).isEmpty();
    }

    @Test
    void coldStartFallsBackToSameCategoryWhenCoPurchaseEmpty() {
        CoPurchaseRepository repo = mock(CoPurchaseRepository.class);
        when(repo.findTopByProductA(eq("source"), any())).thenReturn(List.of());
        ProductProjection sourceProj = new ProductProjection(
                "source", "seller", "src", "books", "img", new BigDecimal("100"), null, 0, 0.0, 0, List.of());
        StubProductPort products = new StubProductPort(Map.of("source", sourceProj));
        // Same-category popularity returns 4 candidates including the source itself.
        products.byCategory.put(
                "books",
                List.of(sourceProj, projection("p-1"), projection("p-2"), projection("p-3"), projection("p-4")));

        FrequentlyBoughtTogetherUseCase useCase = new FrequentlyBoughtTogetherUseCase(repo, products);

        List<ProductProjection> result = useCase.findFor("source", 4);

        // Source filtered out, top 4 same-category remain.
        assertThat(result).extracting(ProductProjection::id).containsExactly("p-1", "p-2", "p-3", "p-4");
    }

    @Test
    void coldStartReturnsEmptyWhenSourceHasNoCategory() {
        CoPurchaseRepository repo = mock(CoPurchaseRepository.class);
        when(repo.findTopByProductA(eq("source"), any())).thenReturn(List.of());
        ProductProjection sourceProj = new ProductProjection(
                "source", "seller", "src", null, "img", new BigDecimal("100"), null, 0, 0.0, 0, List.of());
        StubProductPort products = new StubProductPort(Map.of("source", sourceProj));

        FrequentlyBoughtTogetherUseCase useCase = new FrequentlyBoughtTogetherUseCase(repo, products);

        assertThat(useCase.findFor("source", 4)).isEmpty();
    }

    @Test
    void rejectsBlankProductId() {
        FrequentlyBoughtTogetherUseCase useCase = new FrequentlyBoughtTogetherUseCase(
                mock(CoPurchaseRepository.class), new StubProductPort(Map.of()));

        assertThatThrownBy(() -> useCase.findFor(" ", 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.findFor(null, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroOrNegativeLimitReturnsEmpty() {
        FrequentlyBoughtTogetherUseCase useCase = new FrequentlyBoughtTogetherUseCase(
                mock(CoPurchaseRepository.class), new StubProductPort(Map.of()));

        assertThat(useCase.findFor("source", 0)).isEmpty();
        assertThat(useCase.findFor("source", -1)).isEmpty();
    }

    private static CoPurchaseJpaEntity row(String a, String b, long count) {
        return new CoPurchaseJpaEntity(a, b, count, Instant.now());
    }

    private static ProductProjection projection(String id) {
        return new ProductProjection(id, "seller", "name-" + id, "cat", "img", new BigDecimal("100"), null, 0, 0.0, 0, List.of());
    }

    private static final class StubProductPort implements ProductServicePort {
        private final Map<String, ProductProjection> byId;
        final Map<String, List<ProductProjection>> byCategory = new HashMap<>();

        StubProductPort(Map<String, ProductProjection> byId) {
            this.byId = new HashMap<>(byId);
        }

        @Override
        public Optional<ProductProjection> findById(String productId) {
            return Optional.ofNullable(byId.get(productId));
        }

        @Override
        public List<ProductProjection> listByCategory(String categoryId, int limit) {
            List<ProductProjection> all = byCategory.getOrDefault(categoryId, List.of());
            return all.size() > limit ? all.subList(0, limit) : all;
        }
    }
}
