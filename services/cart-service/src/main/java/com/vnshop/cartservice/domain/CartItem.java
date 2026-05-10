package com.vnshop.cartservice.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

public class CartItem implements Serializable {
    private String productId;
    private String variantSku;
    private String name;
    private int quantity;
    private BigDecimal unitPrice;
    private String imageUrl;

    public CartItem() {
    }

    public CartItem(String productId, String variantSku, String name, int quantity, BigDecimal unitPrice, String imageUrl) {
        this.productId = requireText(productId, "productId");
        this.variantSku = requireText(variantSku, "variantSku");
        this.name = requireText(name, "name");
        setQuantity(quantity);
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        this.imageUrl = imageUrl;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = requireText(productId, "productId");
    }

    public String getVariantSku() {
        return variantSku;
    }

    public void setVariantSku(String variantSku) {
        this.variantSku = requireText(variantSku, "variantSku");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = requireText(name, "name");
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        if (quantity < 1 || quantity > 99) {
            throw new IllegalArgumentException("quantity must be between 1 and 99");
        }
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean matches(String productId, String variantSku) {
        return this.productId.equals(productId) && this.variantSku.equals(variantSku);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
