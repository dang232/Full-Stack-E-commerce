package com.vnshop.productservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class CountSellerProductsBatchUseCaseTest {

    private final ProductRepositoryPort productRepositoryPort = mock(ProductRepositoryPort.class);
    private final CountSellerProductsUseCase useCase = new CountSellerProductsUseCase(productRepositoryPort);

    @Test
    void happyPath_returnsMixedCountsForThreeSellers() {
        Set<String> ids = Set.of("seller-a", "seller-b", "seller-c");
        when(productRepositoryPort.countBySellerIds(ids))
                .thenReturn(Map.of("seller-a", 12L, "seller-b", 0L, "seller-c", 5L));

        Map<String, Long> result = useCase.countAll(ids);

        assertThat(result).containsEntry("seller-a", 12L)
                .containsEntry("seller-b", 0L)
                .containsEntry("seller-c", 5L);
    }

    @Test
    void validation_emptySet_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> useCase.countAll(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        verify(productRepositoryPort, never()).countBySellerIds(anySet());
    }

    @Test
    void validation_moreThan100Ids_throwsIllegalArgumentException() {
        Set<String> tooMany = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "seller-" + i)
                .collect(Collectors.toSet());

        assertThatThrownBy(() -> useCase.countAll(tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
        verify(productRepositoryPort, never()).countBySellerIds(anySet());
    }

    @Test
    void validation_nullSet_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> useCase.countAll(null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(productRepositoryPort, never()).countBySellerIds(anySet());
    }
}
