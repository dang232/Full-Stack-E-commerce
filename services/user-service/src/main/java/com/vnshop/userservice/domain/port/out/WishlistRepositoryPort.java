package com.vnshop.userservice.domain.port.out;

import com.vnshop.userservice.domain.WishlistItem;

import java.util.List;

public interface WishlistRepositoryPort {
    /** Returns the wishlist for a user, ordered by creation time desc. */
    List<WishlistItem> findByKeycloakId(String keycloakId);

    /** Adds the item if not already present. Returns true when a new row was created. */
    boolean add(WishlistItem item);

    /** Removes a single product. Returns true when a row was deleted. */
    boolean remove(String keycloakId, String productId);

    /** Removes every wishlist row for a user. Returns the number of rows deleted. */
    int clear(String keycloakId);

    /** Cheap exists check used by toggle / detail endpoints. */
    boolean exists(String keycloakId, String productId);

    int countByKeycloakId(String keycloakId);
}
