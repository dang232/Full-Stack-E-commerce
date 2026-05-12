package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.CalculateCheckoutUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {
    private final CalculateCheckoutUseCase calculateCheckoutUseCase;

    public CheckoutController(CalculateCheckoutUseCase calculateCheckoutUseCase) {
        this.calculateCheckoutUseCase = calculateCheckoutUseCase;
    }

    @PostMapping("/calculate")
    public ApiResponse<CheckoutBreakdownResponse> calculate(@Valid @RequestBody CalculateCheckoutRequest request) {
        CalculateCheckoutUseCase.CheckoutBreakdown breakdown = calculateCheckoutUseCase.calculate(request.cartId());

        return ApiResponse.ok(CheckoutBreakdownResponse.fromApplication(breakdown));
    }

    @GetMapping("/payment-methods")
    public ApiResponse<List<String>> paymentMethods() {
        return ApiResponse.ok(List.of("COD"));
    }

    @PostMapping("/shipping-options")
    public ApiResponse<List<ShippingOptionResponse>> shippingOptions(@Valid @RequestBody ShippingOptionsRequest request) {
        return ApiResponse.ok(List.of(new ShippingOptionResponse("STANDARD", calculateCheckoutUseCase.standardShippingCost(), "3-5 days")));
    }
}
