package com.vnshop.orderservice.application;

import com.vnshop.orderservice.application.CalculateCheckoutUseCase.CheckoutBreakdown;
import com.vnshop.orderservice.application.CheckoutOrderUseCase.CheckoutLineItem;
import com.vnshop.orderservice.application.CheckoutOrderUseCase.ProductNotFoundException;
import com.vnshop.orderservice.application.catalog.CatalogProduct;
import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.checkout.CartItemSnapshot;
import com.vnshop.orderservice.domain.checkout.CartSnapshot;
import com.vnshop.orderservice.domain.coupon.Coupon;
import com.vnshop.orderservice.domain.coupon.CouponException;
import com.vnshop.orderservice.domain.coupon.CouponValidator;
import com.vnshop.orderservice.domain.port.out.CartRepositoryPort;
import com.vnshop.orderservice.domain.port.out.ProductCatalogPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two preview paths share a use case:
 * <ol>
 *   <li>{@code calculate(cartId)} — legacy, still used by the cart-snapshot
 *       flow. Sums prices from the cart-service snapshot.</li>
 *   <li>{@code calculate(lineItems)} — new light-shape preview. Mirrors the
 *       {@code POST /orders} contract (client sends only ids + qty). Closes
 *       the security boundary so a checkout-preview cannot leak a
 *       client-set price into the displayed total.</li>
 * </ol>
 */
class CalculateCheckoutUseCaseTest {

    private final FakeCartRepository cart = new FakeCartRepository();
    private final FakeProductCatalog catalog = new FakeProductCatalog();
    private final CalculateCheckoutUseCase useCase = new CalculateCheckoutUseCase(cart, catalog);

    @Test
    void cartSnapshotPathSumsLineTotalsAndAddsStandardShipping() {
        cart.set("cart-1", new CartSnapshot("cart-1", List.of(
                new CartItemSnapshot("p1", "sku1", "Item 1", 2, new BigDecimal("100000")),
                new CartItemSnapshot("p2", "sku2", "Item 2", 1, new BigDecimal("250000")))));

        CheckoutBreakdown breakdown = useCase.calculate("cart-1");

        assertThat(breakdown.itemsTotal()).isEqualByComparingTo("450000");
        assertThat(breakdown.shippingEstimate()).isEqualByComparingTo("30000");
        assertThat(breakdown.discount()).isEqualByComparingTo("0");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("480000");
    }

    @Test
    void lineItemPathResolvesAuthoritativePriceFromCatalogIgnoringClientInput() {
        catalog.add(new CatalogProduct(
                "p1", "seller-A", "Authoritative Product",
                List.of(new CatalogProduct.Variant("sku1", new Money(new BigDecimal("199000"), "VND"))),
                ""));

        CheckoutBreakdown breakdown = useCase.calculate(List.of(new CheckoutLineItem("p1", "sku1", 2)));

        // 199000 * 2 + 30000 standard shipping = 428000.
        assertThat(breakdown.itemsTotal()).isEqualByComparingTo("398000");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("428000");
    }

    @Test
    void lineItemPathDefaultsToFirstVariantWhenSkuOmitted() {
        catalog.add(new CatalogProduct(
                "p1", "seller-A", "Multi-variant",
                List.of(
                        new CatalogProduct.Variant("default", new Money(new BigDecimal("100000"), "VND")),
                        new CatalogProduct.Variant("alt", new Money(new BigDecimal("150000"), "VND"))),
                ""));

        CheckoutBreakdown breakdown = useCase.calculate(List.of(new CheckoutLineItem("p1", null, 1)));

        assertThat(breakdown.itemsTotal()).isEqualByComparingTo("100000");
    }

    @Test
    void lineItemPathRejectsMissingProduct() {
        assertThatThrownBy(() -> useCase.calculate(List.of(new CheckoutLineItem("missing", null, 1))))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void lineItemPathRejectsEmptyList() {
        assertThatThrownBy(() -> useCase.calculate(List.<CheckoutLineItem>of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items");
    }

    /**
     * Coupon-aware preview: the BE resolves the discount via CouponValidator
     * so the FE never gets to set a discount amount. This is the path that
     * the BA-grade journey suite's AC-2.2 exercises end-to-end.
     */
    @Test
    void lineItemPathHonoursCouponCodeWhenValidatorAcceptsIt() {
        catalog.add(new CatalogProduct(
                "p1", "seller-A", "Product",
                List.of(new CatalogProduct.Variant("sku1", new Money(new BigDecimal("200000"), "VND"))),
                ""));

        CouponValidator validator = mock(CouponValidator.class);
        Coupon coupon = mock(Coupon.class);
        when(validator.validate(eq("SAVE50"), any(Money.class), eq("user-1")))
                .thenReturn(new CouponValidator.ValidationResult(
                        true, coupon, new Money(new BigDecimal("50000")), null, null));

        CalculateCheckoutUseCase useCaseWithCoupon =
                new CalculateCheckoutUseCase(cart, catalog, validator);

        CheckoutBreakdown breakdown = useCaseWithCoupon.calculate(
                List.of(new CheckoutLineItem("p1", "sku1", 1)),
                "SAVE50",
                "user-1");

        // 200000 items + 30000 shipping - 50000 discount = 180000.
        assertThat(breakdown.itemsTotal()).isEqualByComparingTo("200000");
        assertThat(breakdown.discount()).isEqualByComparingTo("50000");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("180000");
    }

    @Test
    void couponPathSilentlyReturnsZeroDiscountWhenValidatorRejects() {
        catalog.add(new CatalogProduct(
                "p1", "seller-A", "Product",
                List.of(new CatalogProduct.Variant("sku1", new Money(new BigDecimal("200000"), "VND"))),
                ""));

        CouponValidator validator = mock(CouponValidator.class);
        when(validator.validate(eq("EXPIRED"), any(Money.class), any()))
                .thenThrow(new CouponException("COUPON_EXPIRED", "Coupon expired"));

        CalculateCheckoutUseCase useCaseWithCoupon =
                new CalculateCheckoutUseCase(cart, catalog, validator);

        CheckoutBreakdown breakdown = useCaseWithCoupon.calculate(
                List.of(new CheckoutLineItem("p1", "sku1", 1)),
                "EXPIRED",
                "user-1");

        // Invalid coupon → preview stays interactive with zero discount; the
        // /checkout/apply-coupon round-trip is what surfaces the reason.
        assertThat(breakdown.discount()).isEqualByComparingTo("0");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("230000");
    }

    @Test
    void couponPathTreatsBlankCodeAsAbsent() {
        catalog.add(new CatalogProduct(
                "p1", "seller-A", "Product",
                List.of(new CatalogProduct.Variant("sku1", new Money(new BigDecimal("200000"), "VND"))),
                ""));

        CouponValidator validator = mock(CouponValidator.class);
        CalculateCheckoutUseCase useCaseWithCoupon =
                new CalculateCheckoutUseCase(cart, catalog, validator);

        CheckoutBreakdown breakdown = useCaseWithCoupon.calculate(
                List.of(new CheckoutLineItem("p1", "sku1", 1)),
                "   ",
                "user-1");

        assertThat(breakdown.discount()).isEqualByComparingTo("0");
    }

    private static final class FakeCartRepository implements CartRepositoryPort {
        private final Map<String, CartSnapshot> carts = new HashMap<>();

        void set(String cartId, CartSnapshot snapshot) { carts.put(cartId, snapshot); }

        @Override public CartSnapshot findByCartId(String cartId) {
            return carts.getOrDefault(cartId, new CartSnapshot(cartId, List.of()));
        }
    }

    private static final class FakeProductCatalog implements ProductCatalogPort {
        private final Map<String, CatalogProduct> products = new HashMap<>();

        void add(CatalogProduct product) { products.put(product.productId(), product); }

        @Override public Optional<CatalogProduct> findByProductId(String productId) {
            return Optional.ofNullable(products.get(productId));
        }
    }
}
