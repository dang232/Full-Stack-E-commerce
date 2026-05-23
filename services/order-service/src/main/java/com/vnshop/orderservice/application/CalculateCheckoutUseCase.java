package com.vnshop.orderservice.application;

import com.vnshop.orderservice.application.CheckoutOrderUseCase.CheckoutLineItem;
import com.vnshop.orderservice.application.catalog.CatalogProduct;
import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.checkout.CartItemSnapshot;
import com.vnshop.orderservice.domain.checkout.CartSnapshot;
import com.vnshop.orderservice.domain.coupon.CouponException;
import com.vnshop.orderservice.domain.coupon.CouponValidator;
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
    private final CouponValidator couponValidator;

    public CalculateCheckoutUseCase(CartRepositoryPort cartRepositoryPort, ProductCatalogPort productCatalogPort) {
        this(cartRepositoryPort, productCatalogPort, null);
    }

    public CalculateCheckoutUseCase(
            CartRepositoryPort cartRepositoryPort,
            ProductCatalogPort productCatalogPort,
            CouponValidator couponValidator) {
        this.cartRepositoryPort = Objects.requireNonNull(cartRepositoryPort, "cartRepositoryPort is required");
        this.productCatalogPort = Objects.requireNonNull(productCatalogPort, "productCatalogPort is required");
        // couponValidator is optional — when null, all calculate(...) calls
        // produce zero discount (legacy behaviour). When provided, the
        // line-item path honours the caller's couponCode.
        this.couponValidator = couponValidator;
    }

    public CheckoutBreakdown calculate(String cartId) {
        CartSnapshot cart = cartRepositoryPort.findByCartId(cartId);
        BigDecimal itemsTotal = cart.items().stream()
                .map(CartItemSnapshot::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return summarize(itemsTotal, NO_DISCOUNT);
    }

    /**
     * Light-shape preview without coupon resolution. Kept for backward
     * compatibility with callers (and tests) that don't need coupon math.
     */
    public CheckoutBreakdown calculate(List<CheckoutLineItem> lineItems) {
        return calculate(lineItems, null, null);
    }

    /**
     * Light-shape preview: client sends {@code (productId, variantSku?, quantity)}
     * tuples; we resolve each to its authoritative catalog price and sum. Mirrors
     * the contract used by {@link CheckoutOrderUseCase} — same security boundary,
     * client-supplied prices are structurally impossible.
     *
     * <p>When {@code couponCode} is non-blank and a {@link CouponValidator}
     * is wired, the discount is resolved server-side: the FE never sets
     * the discount amount, only proposes a code. An invalid/expired code
     * silently produces zero discount instead of 4xxing the preview — the
     * caller's {@code /checkout/apply-coupon} round-trip is the place that
     * surfaces "your coupon is invalid" errors.</p>
     *
     * <p><b>Architectural caveat:</b> The platform currently has two coupon
     * stores. {@code coupon-service} owns admin coupon CRUD + {@code
     * /checkout/apply-coupon}. {@code order-service} has its own local
     * {@link CouponValidator} backed by a separate DB (legacy path used at
     * place-order time). When admin publishes a coupon via the FE admin UI,
     * the row lands in coupon-service's DB only. This preview path's
     * validator queries order-service's DB and won't find the new code,
     * so the discount silently resolves to zero. A follow-up will add a
     * cross-service port (HTTP call to coupon-service's validate endpoint)
     * so the preview honours the same source of truth as place-order. Until
     * then, AC-2.2 of the BA-grade journey suite stays red as the visible
     * signal that the gap is open.</p>
     */
    public CheckoutBreakdown calculate(List<CheckoutLineItem> lineItems, String couponCode, String userId) {
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
        return summarize(itemsTotal, resolveDiscount(itemsTotal, couponCode, userId));
    }

    public BigDecimal standardShippingCost() {
        return STANDARD_SHIPPING_COST;
    }

    private BigDecimal resolveDiscount(BigDecimal itemsTotal, String couponCode, String userId) {
        if (couponCode == null || couponCode.isBlank() || couponValidator == null) {
            return NO_DISCOUNT;
        }
        try {
            CouponValidator.ValidationResult result = couponValidator.validate(
                    couponCode, new Money(itemsTotal), userId);
            if (!result.valid() || result.discount() == null) return NO_DISCOUNT;
            // Cap the discount at the items subtotal so we never invert the
            // cart total (the validator already does this, but defence in
            // depth keeps the contract clean for future coupon types).
            BigDecimal discount = result.discount().amount();
            return discount.compareTo(itemsTotal) > 0 ? itemsTotal : discount;
        } catch (CouponException ignored) {
            // Validation failures (expired / not-found / etc.) return zero
            // here so the preview stays interactive. The buyer's actual
            // /checkout/apply-coupon attempt is what surfaces the reason.
            return NO_DISCOUNT;
        }
    }

    private CheckoutBreakdown summarize(BigDecimal itemsTotal, BigDecimal discount) {
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
