package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.OrderItem;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderItemRequest(
        @NotBlank String productId,
        @NotBlank String variantSku,
        @NotBlank String sellerId,
        @NotBlank String name,
        @Min(1) int quantity,
        @NotNull BigDecimal unitPriceAmount,
        String unitPriceCurrency,
        String imageUrl
) {

    OrderItem toDomain() {
        return new OrderItem(productId, variantSku, sellerId, name, quantity, new Money(unitPriceAmount, unitPriceCurrency), imageUrl);
    }
}
