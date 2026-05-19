package com.vnshop.userservice.infrastructure.web;

/**
 * @param productId  the affected product
 * @param changed    true when the call mutated state (added or removed)
 * @param inWishlist the resulting "is on the wishlist" state for this product
 */
public record WishlistToggleResponse(String productId, boolean changed, boolean inWishlist) {}
