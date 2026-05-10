package com.vnshop.orderservice.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {
    private static final BigDecimal STANDARD_SHIPPING_COST = BigDecimal.valueOf(30000);
    private static final BigDecimal NO_DISCOUNT = BigDecimal.ZERO;

    private final CartRepositoryPort cartRepositoryPort;

    public CheckoutController() {
        this(new StubCartRepositoryPort());
    }

    CheckoutController(CartRepositoryPort cartRepositoryPort) {
        this.cartRepositoryPort = cartRepositoryPort;
    }

    @PostMapping("/calculate")
    public CheckoutBreakdownResponse calculate(@Valid @RequestBody CalculateCheckoutRequest request) {
        CartSnapshot cart = cartRepositoryPort.findByCartId(request.cartId());
        BigDecimal itemsTotal = cart.items().stream()
                .map(CartItemSnapshot::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = NO_DISCOUNT;
        BigDecimal finalAmount = itemsTotal.add(STANDARD_SHIPPING_COST).subtract(discount);

        return new CheckoutBreakdownResponse(itemsTotal, STANDARD_SHIPPING_COST, discount, finalAmount);
    }

    @GetMapping("/payment-methods")
    public List<String> paymentMethods() {
        return List.of("COD");
    }

    @PostMapping("/shipping-options")
    public List<ShippingOptionResponse> shippingOptions(@Valid @RequestBody ShippingOptionsRequest request) {
        return List.of(new ShippingOptionResponse("STANDARD", STANDARD_SHIPPING_COST, "3-5 days"));
    }

    public record CalculateCheckoutRequest(
            @NotBlank String cartId,
            @Valid @NotNull AddressRequest shippingAddress,
            String couponCode
    ) {
    }

    public record ShippingOptionsRequest(@Valid @NotNull AddressRequest address) {
    }

    public record AddressRequest(@NotBlank String street, String ward, @NotBlank String district, @NotBlank String city) {
    }

    public record CheckoutBreakdownResponse(
            BigDecimal itemsTotal,
            BigDecimal shippingEstimate,
            BigDecimal discount,
            BigDecimal finalAmount
    ) {
    }

    public record ShippingOptionResponse(String method, BigDecimal cost, String estimate) {
    }

    interface CartRepositoryPort {
        CartSnapshot findByCartId(String cartId);
    }

    record CartSnapshot(String cartId, List<CartItemSnapshot> items) {
    }

    record CartItemSnapshot(String productId, String variantSku, String name, int quantity, BigDecimal unitPrice) {
        BigDecimal total() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    static class StubCartRepositoryPort implements CartRepositoryPort {
        @Override
        public CartSnapshot findByCartId(String cartId) {
            return new CartSnapshot(cartId, List.of(
                    new CartItemSnapshot("phase-1-product", "STANDARD", "Phase 1 checkout item", 1, BigDecimal.valueOf(100000))
            ));
        }
    }
}
