package com.vnshop.productservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;
import org.junit.jupiter.api.Test;

class CountSellerProductsUseCaseTest {

    private final ProductRepositoryPort productRepositoryPort = mock(ProductRepositoryPort.class);
    private final CountSellerProductsUseCase useCase = new CountSellerProductsUseCase(productRepositoryPort);

    @Test
    void returnsCountFromRepository() {
        when(productRepositoryPort.countBySellerId("seller-1")).thenReturn(5L);

        long result = useCase.count("seller-1");

        assertThat(result).isEqualTo(5L);
    }

    @Test
    void returnsZeroWhenSellerHasNoProducts() {
        when(productRepositoryPort.countBySellerId("seller-empty")).thenReturn(0L);

        long result = useCase.count("seller-empty");

        assertThat(result).isZero();
    }

    @Test
    void rejectsNullRepository() {
        assertThatThrownBy(() -> new CountSellerProductsUseCase(null))
                .isInstanceOf(NullPointerException.class);
    }
}
