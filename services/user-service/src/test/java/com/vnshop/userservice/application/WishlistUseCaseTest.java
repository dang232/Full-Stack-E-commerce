package com.vnshop.userservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vnshop.userservice.domain.WishlistItem;
import com.vnshop.userservice.domain.port.out.WishlistRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

class WishlistUseCaseTest {
    private WishlistRepositoryPort repository;
    private WishlistUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(WishlistRepositoryPort.class);
        useCase = new WishlistUseCase(repository);
    }

    @Test
    void list_returnsItemsForUser() {
        List<WishlistItem> items = List.of(
            new WishlistItem("kc-1", "p1", Instant.parse("2026-05-01T00:00:00Z")));
        when(repository.findByKeycloakId("kc-1")).thenReturn(items);

        assertThat(useCase.list("kc-1")).isEqualTo(items);
    }

    @Test
    void add_returnsTrueWhenNew_andDelegatesToRepository() {
        when(repository.exists("kc-1", "p1")).thenReturn(false);
        when(repository.countByKeycloakId("kc-1")).thenReturn(0);
        when(repository.add(any(WishlistItem.class))).thenReturn(true);

        assertThat(useCase.add("kc-1", "p1")).isTrue();
        verify(repository, times(1)).add(any(WishlistItem.class));
    }

    @Test
    void add_returnsFalseWhenAlreadyPresent_andDoesNotCountTowardCap() {
        when(repository.exists("kc-1", "p1")).thenReturn(true);
        when(repository.add(any(WishlistItem.class))).thenReturn(false);

        assertThat(useCase.add("kc-1", "p1")).isFalse();
        // Even if user is "at cap", re-adding an existing item must not throw,
        // because we're not actually creating a new row.
        verify(repository, never()).countByKeycloakId(any());
    }

    @Test
    void add_throwsWhenAtCapAndProductIsNew() {
        when(repository.exists("kc-1", "p-new")).thenReturn(false);
        when(repository.countByKeycloakId("kc-1")).thenReturn(WishlistUseCase.MAX_ITEMS);

        assertThatThrownBy(() -> useCase.add("kc-1", "p-new"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("wishlist is full");
        verify(repository, never()).add(any(WishlistItem.class));
    }

    @Test
    void add_rejectsBlankProductId() {
        assertThatThrownBy(() -> useCase.add("kc-1", "  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remove_delegatesToRepository() {
        when(repository.remove("kc-1", "p1")).thenReturn(true);

        assertThat(useCase.remove("kc-1", "p1")).isTrue();
        verify(repository).remove("kc-1", "p1");
    }

    @Test
    void remove_returnsFalseWhenNotPresent() {
        when(repository.remove("kc-1", "p-missing")).thenReturn(false);

        assertThat(useCase.remove("kc-1", "p-missing")).isFalse();
    }

    @Test
    void clear_returnsRowCount() {
        when(repository.clear("kc-1")).thenReturn(7);

        assertThat(useCase.clear("kc-1")).isEqualTo(7);
    }

    @Test
    void toggle_addsWhenAbsent_returnsTrue() {
        when(repository.exists("kc-1", "p1")).thenReturn(false);
        when(repository.countByKeycloakId("kc-1")).thenReturn(0);

        assertThat(useCase.toggle("kc-1", "p1")).isTrue();
        verify(repository).add(any(WishlistItem.class));
        verify(repository, never()).remove(eq("kc-1"), eq("p1"));
    }

    @Test
    void toggle_removesWhenPresent_returnsFalse() {
        when(repository.exists("kc-1", "p1")).thenReturn(true);

        assertThat(useCase.toggle("kc-1", "p1")).isFalse();
        verify(repository).remove("kc-1", "p1");
        verify(repository, never()).add(any(WishlistItem.class));
    }

    @Test
    void toggle_throwsWhenAtCapAndProductIsNew() {
        when(repository.exists("kc-1", "p-new")).thenReturn(false);
        when(repository.countByKeycloakId("kc-1")).thenReturn(WishlistUseCase.MAX_ITEMS);

        assertThatThrownBy(() -> useCase.toggle("kc-1", "p-new"))
            .isInstanceOf(IllegalStateException.class);
    }
}
