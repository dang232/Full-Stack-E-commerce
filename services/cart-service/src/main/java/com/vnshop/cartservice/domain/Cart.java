package com.vnshop.cartservice.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Cart implements Serializable {
    private String buyerId;
    private List<CartItem> items = new ArrayList<>();
    private Instant updatedAt;

    public Cart() {
    }

    public Cart(String buyerId) {
        this.buyerId = requireText(buyerId, "buyerId");
        this.updatedAt = Instant.now();
    }

    public String getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = requireText(buyerId, "buyerId");
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void addOrIncrement(CartItem incoming) {
        for (CartItem item : items) {
            if (item.matches(incoming.getProductId(), incoming.getVariantSku())) {
                item.setQuantity(item.getQuantity() + incoming.getQuantity());
                touch();
                return;
            }
        }
        items.add(incoming);
        touch();
    }

    public void updateQuantity(String productId, String variantSku, int quantity) {
        if (quantity == 0) {
            removeItem(productId, variantSku);
            return;
        }
        for (CartItem item : items) {
            if (item.matches(productId, variantSku)) {
                item.setQuantity(quantity);
                touch();
                return;
            }
        }
    }

    public void removeItem(String productId, String variantSku) {
        if (items.removeIf(item -> item.matches(productId, variantSku))) {
            touch();
        }
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
