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
    // Static catalog of payment methods the FE renders on the checkout page.
    // The shape matches paymentMethodSchema (code/name/description/enabled) so
    // the FE can drop its FALLBACK_PAYMENT mirror. COD is always on; VNPAY and
    // MOMO have provider integrations in payment-service. A follow-up will move
    // this to @ConfigurationProperties so a deploy can toggle providers without
    // a code change.
    private static final List<PaymentMethodResponse> PAYMENT_METHODS = List.of(
            new PaymentMethodResponse(
                    "COD",
                    "Cash on Delivery",
                    "Pay with cash when the order is delivered",
                    true),
            new PaymentMethodResponse(
                    "VNPAY",
                    "VNPay",
                    "Pay online via VNPay (ATM, QR, internet banking)",
                    true),
            new PaymentMethodResponse(
                    "MOMO",
                    "MoMo",
                    "Pay with the MoMo e-wallet",
                    true));

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
    public ApiResponse<List<PaymentMethodResponse>> paymentMethods() {
        return ApiResponse.ok(PAYMENT_METHODS);
    }

    @PostMapping("/shipping-options")
    public ApiResponse<List<ShippingOptionResponse>> shippingOptions(@Valid @RequestBody ShippingOptionsRequest request) {
        return ApiResponse.ok(List.of(new ShippingOptionResponse("STANDARD", calculateCheckoutUseCase.standardShippingCost(), "3-5 days")));
    }
}

