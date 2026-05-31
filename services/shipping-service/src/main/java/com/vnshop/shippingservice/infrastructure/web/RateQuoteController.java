package com.vnshop.shippingservice.infrastructure.web;

import com.vnshop.shippingservice.application.QuoteShippingOptionsUseCase;
import com.vnshop.shippingservice.domain.model.Parcel;
import com.vnshop.shippingservice.domain.model.RateQuote;
import com.vnshop.shippingservice.domain.model.ShippingAddress;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public rate-quote endpoint. Buyer-facing shipping options for a given
 * destination address + parcel size, returned as a list so the FE checkout
 * can render the buyer's choice. Anonymous-safe — pricing is per-shipment,
 * not per-account.
 */
@RestController
public class RateQuoteController {
    private final QuoteShippingOptionsUseCase useCase;

    public RateQuoteController(QuoteShippingOptionsUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/shipping/rate-quotes")
    public ApiResponse<RateQuotesResponse> quote(@Valid @RequestBody RateQuoteHttpRequest request) {
        ShippingAddress destination = new ShippingAddress(
                request.recipientName() != null ? request.recipientName() : "Recipient",
                request.recipientPhone(),
                request.street(),
                request.ward(),
                request.district(),
                request.province()
        );
        Parcel parcel = request.parcel() != null
                ? new Parcel(
                        request.parcel().weightGrams(),
                        request.parcel().lengthCm(),
                        request.parcel().widthCm(),
                        request.parcel().heightCm())
                // Default parcel: 1kg / 30x20x10cm. Matches a typical
                // multi-item retail order; covers cases where the FE doesn't
                // know exact dimensions yet.
                : new Parcel(1000, 30, 20, 10);

        List<RateQuote> quotes = useCase.quoteOptions(destination, parcel);
        List<RateQuoteResponse> options = quotes.stream()
                .map(q -> new RateQuoteResponse(
                        q.carrier().name(),
                        q.serviceCode(),
                        q.totalFeeVnd(),
                        q.estimatedDeliveryTime()))
                .toList();
        return ApiResponse.ok(new RateQuotesResponse(options));
    }
}
