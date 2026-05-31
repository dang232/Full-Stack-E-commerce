package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.port.out.WishlistRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WishlistUseCaseEdgeCasesTest {

    private WishlistRepositoryPort repository;
    private WishlistUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(WishlistRepositoryPort.class);
        useCase = new WishlistUseCase(repository);
    }

    @Test
    void add_nullProductId_throws() {
        assertThatThrownBy(() -> useCase.add("kc-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void remove_nullProductId_throws() {
        assertThatThrownBy(() -> useCase.remove("kc-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void remove_blankProductId_throws() {
        assertThatThrownBy(() -> useCase.remove("kc-1", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void toggle_nullProductId_throws() {
        assertThatThrownBy(() -> useCase.toggle("kc-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void toggle_blankProductId_throws() {
        assertThatThrownBy(() -> useCase.toggle("kc-1", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void constructor_nullRepository_throws() {
        assertThatThrownBy(() -> new WishlistUseCase(null))
                .isInstanceOf(NullPointerException.class);
    }
}
