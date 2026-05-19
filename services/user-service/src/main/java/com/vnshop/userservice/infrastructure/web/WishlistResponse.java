package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.domain.WishlistItem;
import java.util.List;

public record WishlistResponse(List<String> productIds, List<WishlistEntry> items) {
    public static WishlistResponse fromDomain(List<WishlistItem> items) {
        return new WishlistResponse(
                items.stream().map(WishlistItem::productId).toList(),
                items.stream()
                        .map(item -> new WishlistEntry(item.productId(), item.createdAt()))
                        .toList());
    }
}
