package com.vnshop.orderservice.application;

import com.vnshop.orderservice.application.CheckoutOrderUseCase.CheckoutLineItem;
import com.vnshop.orderservice.application.catalog.CatalogProduct;
import com.vnshop.orderservice.domain.checkout.CartItemSnapshot;
import com.vnshop.orderservice.domain.checkout.CartSnapshot;
import com.vnshop.orderservice.domain.port.out.CartRepositoryPort;
import com.vnshop.orderservice.domain.port.out.ProductCatalogPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public class CalculateCheckoutUseCase {
    private static final BigDecimal STANDARD_SHIPPING_COST = BigDecimal.valueOf(30000);
    private static final BigDecimal NO_DISCOUNT = BigDecimal.ZERO;

    private final CartRepositoryPort cartRepositoryPort;
    private final ProductCatalogPort productCatalogPort;

    public CalculateCheckoutUseCase(CartRepositoryPort cartRepositoryPort, ProductCatalogPort productCatalogPort) {
        this.cartRepositoryPort = Objects.requireNonNull(cartRepositoryPort, "cartRepositoryPort is required");
        this.productCatalogPort = Objects.requireNonNull(productCatalogPort, "productCatalogPort is required");
    }

    public CheckoutBreakdown calculate(String cartId) {
        CartSnapshot cart = cartRepositoryPort.findByCartId(cartId);
        BigDecimal itemsTotal = cart.items().stream()
                .map(CartItemSnapshot::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return summarize(itemsTotal);
    }

    /**
     * Light-shape preview: client sends {@code (productId, variantSku?, quantity)}
     * tuples; we resolve each to its authoritative catalog price and sum. Mirrors
     * the contract used by {@link CheckoutOrderUseCase} — same security boundary,
     * client-supplied prices are structurally impossible.
     */
    public CheckoutBreakdown calculate(List<CheckoutLineItem> lineItems) {
        if (lineItems == null || lineItems.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (CheckoutLineItem line : lineItems) {
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("quantity must be > 0 for productId=" + line.productId());
            }
            CatalogProduct product = productCatalogPort.findByProductId(line.productId())
                    .orElseThrow(() -> new CheckoutOrderUseCase.ProductNotFoundException(
                            "product not found: " + line.productId()));
            CatalogProduct.Variant variant = product.findVariant(line.variantSku())
                    .orElseThrow(() -> new CheckoutOrderUseCase.ProductNotFoundException(
                            "variant not found for productId=" + line.productId() + " sku=" + line.variantSku()));
            itemsTotal = itemsTotal.add(variant.unitPrice().amount().multiply(BigDecimal.valueOf(line.quantity())));
        }
        return summarize(itemsTotal);
    }

    public BigDecimal standardShippingCost() {
        return STANDARD_SHIPPING_COST;
    }

    private CheckoutBreakdown summarize(BigDecimal itemsTotal) {
        BigDecimal discount = NO_DISCOUNT;
        BigDecimal finalAmount = itemsTotal.add(STANDARD_SHIPPING_COST).subtract(discount);
        return new CheckoutBreakdown(itemsTotal, STANDARD_SHIPPING_COST, discount, finalAmount);
    }

    public record CheckoutBreakdown(
            BigDecimal itemsTotal,
            BigDecimal shippingEstimate,
            BigDecimal discount,
            BigDecimal finalAmount
    ) {
    }
}
