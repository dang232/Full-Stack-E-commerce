package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.domain.checkout.CartItemSnapshot;
import com.vnshop.orderservice.domain.checkout.CartSnapshot;
import com.vnshop.orderservice.domain.port.out.CartRepositoryPort;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {
    private static final BigDecimal STANDARD_SHIPPING_COST = BigDecimal.valueOf(30000);
    private static final BigDecimal NO_DISCOUNT = BigDecimal.ZERO;

    private final CartRepositoryPort cartRepositoryPort;

    public CheckoutController(CartRepositoryPort cartRepositoryPort) {
        this.cartRepositoryPort = cartRepositoryPort;
    }

    @PostMapping("/calculate")
    public ApiResponse<CheckoutBreakdownResponse> calculate(@Valid @RequestBody CalculateCheckoutRequest request) {
        CartSnapshot cart = cartRepositoryPort.findByCartId(request.cartId());
        BigDecimal itemsTotal = cart.items().stream()
                .map(CartItemSnapshot::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = NO_DISCOUNT;
        BigDecimal finalAmount = itemsTotal.add(STANDARD_SHIPPING_COST).subtract(discount);

        return ApiResponse.ok(new CheckoutBreakdownResponse(itemsTotal, STANDARD_SHIPPING_COST, discount, finalAmount));
    }

    @GetMapping("/payment-methods")
    public ApiResponse<List<String>> paymentMethods() {
        return ApiResponse.ok(List.of("COD"));
    }

    @PostMapping("/shipping-options")
    public ApiResponse<List<ShippingOptionResponse>> shippingOptions(@Valid @RequestBody ShippingOptionsRequest request) {
        return ApiResponse.ok(List.of(new ShippingOptionResponse("STANDARD", STANDARD_SHIPPING_COST, "3-5 days")));
    }
}
