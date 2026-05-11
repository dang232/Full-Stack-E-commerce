package com.vnshop.orderservice.domain.checkout;

import java.math.BigDecimal;

public record CartItemSnapshot(String productId, String variantSku, String name, int quantity, BigDecimal unitPrice) {
    public BigDecimal total() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
