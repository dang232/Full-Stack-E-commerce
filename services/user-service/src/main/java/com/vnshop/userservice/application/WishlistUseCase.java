package com.vnshop.userservice.application;

import com.vnshop.userservice.domain.WishlistItem;
import com.vnshop.userservice.domain.port.out.WishlistRepositoryPort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Buyer-facing wishlist operations: list, add, remove, clear, toggle.
 * <p>The service caps each user at {@value #MAX_ITEMS} entries so a hostile
 * client can't fill the table by spamming POST.
 */
public class WishlistUseCase {
    public static final int MAX_ITEMS = 200;

    private final WishlistRepositoryPort repository;

    public WishlistUseCase(WishlistRepositoryPort repository) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
    }

    public List<WishlistItem> list(String keycloakId) {
        return repository.findByKeycloakId(keycloakId);
    }

    /** @return true if the item was newly added; false if it was already in the wishlist. */
    public boolean add(String keycloakId, String productId) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (!repository.exists(keycloakId, productId)
                && repository.countByKeycloakId(keycloakId) >= MAX_ITEMS) {
            throw new IllegalStateException(
                    "wishlist is full (max " + MAX_ITEMS + " items)");
        }
        return repository.add(new WishlistItem(keycloakId, productId, Instant.now()));
    }

    /** @return true if a row was removed; false if the item wasn't in the wishlist. */
    public boolean remove(String keycloakId, String productId) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        return repository.remove(keycloakId, productId);
    }

    /** Clears the entire wishlist. Returns the number of rows removed. */
    public int clear(String keycloakId) {
        return repository.clear(keycloakId);
    }

    /**
     * Atomic-ish toggle: if present, removes; if absent, adds. Returns the
     * resulting "is in wishlist" state from the caller's perspective.
     */
    public boolean toggle(String keycloakId, String productId) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (repository.exists(keycloakId, productId)) {
            repository.remove(keycloakId, productId);
            return false;
        }
        if (repository.countByKeycloakId(keycloakId) >= MAX_ITEMS) {
            throw new IllegalStateException(
                    "wishlist is full (max " + MAX_ITEMS + " items)");
        }
        repository.add(new WishlistItem(keycloakId, productId, Instant.now()));
        return true;
    }
}
