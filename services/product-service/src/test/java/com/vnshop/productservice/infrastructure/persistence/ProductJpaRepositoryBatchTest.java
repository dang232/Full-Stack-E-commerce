package com.vnshop.productservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class ProductJpaRepositoryBatchTest {

    private final ProductJpaSpringDataRepository springDataRepository = mock(ProductJpaSpringDataRepository.class);
    private final ProductJpaRepository adapter = new ProductJpaRepository(springDataRepository);

    @Test
    void countBySellerIds_skipsDbWhenSetIsEmpty() {
        Map<String, Long> result = adapter.countBySellerIds(Set.of());

        assertThat(result).isEmpty();
        verify(springDataRepository, never()).countBySellerIds(anyCollection());
    }

    @Test
    void countBySellerIds_fillsMissingSellerWithZero() {
        Set<String> ids = Set.of("seller-a", "seller-b");
        // DB only returns a row for seller-a (seller-b has no products)
        when(springDataRepository.countBySellerIds(ids))
                .thenReturn(List.<Object[]>of(new Object[]{"seller-a", 7L}));

        Map<String, Long> result = adapter.countBySellerIds(ids);

        assertThat(result).containsEntry("seller-a", 7L)
                .containsEntry("seller-b", 0L);
    }
}
