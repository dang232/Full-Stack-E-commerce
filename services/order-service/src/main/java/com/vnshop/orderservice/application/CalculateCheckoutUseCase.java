package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.checkout.CartItemSnapshot;
import com.vnshop.orderservice.domain.checkout.CartSnapshot;
import com.vnshop.orderservice.domain.port.out.CartRepositoryPort;
import java.math.BigDecimal;

public class CalculateCheckoutUseCase {
    private static final BigDecimal STANDARD_SHIPPING_COST = BigDecimal.valueOf(30000);
    private static final BigDecimal NO_DISCOUNT = BigDecimal.ZERO;

    private final CartRepositoryPort cartRepositoryPort;

    public CalculateCheckoutUseCase(CartRepositoryPort cartRepositoryPort) {
        this.cartRepositoryPort = cartRepositoryPort;
    }

    public CheckoutBreakdown calculate(String cartId) {
        CartSnapshot cart = cartRepositoryPort.findByCartId(cartId);
        BigDecimal itemsTotal = cart.items().stream()
                .map(CartItemSnapshot::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = NO_DISCOUNT;
        BigDecimal finalAmount = itemsTotal.add(STANDARD_SHIPPING_COST).subtract(discount);

        return new CheckoutBreakdown(itemsTotal, STANDARD_SHIPPING_COST, discount, finalAmount);
    }

    public BigDecimal standardShippingCost() {
        return STANDARD_SHIPPING_COST;
    }

    public record CheckoutBreakdown(
            BigDecimal itemsTotal,
            BigDecimal shippingEstimate,
            BigDecimal discount,
            BigDecimal finalAmount
    ) {
    }
}
