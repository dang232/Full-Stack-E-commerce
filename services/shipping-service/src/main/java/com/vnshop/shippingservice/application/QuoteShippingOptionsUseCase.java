package com.vnshop.shippingservice.application;

import com.vnshop.shippingservice.domain.model.CarrierCode;
import com.vnshop.shippingservice.domain.model.Parcel;
import com.vnshop.shippingservice.domain.model.RateQuote;
import com.vnshop.shippingservice.domain.model.RateQuoteRequest;
import com.vnshop.shippingservice.domain.model.ShippingAddress;
import com.vnshop.shippingservice.domain.port.out.CarrierGatewayPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Returns the set of shipping options a buyer can pick from at checkout.
 *
 * <p>Drives a quote-per-tier loop against {@link CarrierGatewayPort#quote}: the
 * carrier-side query is the only source of truth for fee + ETA, and individual
 * carriers may return different costs per tier (GHN's express vs standard, etc).
 * The use case stitches the per-tier responses into a single buyer-facing list.
 *
 * <p>STANDARD vs EXPRESS is encoded as a {@link CarrierCode} pick rather than
 * a parcel-flag because the existing stub + live gateways already discriminate
 * by carrier. A future enhancement could add a {@code serviceCode} parameter
 * for sub-tiers within one carrier, but it's not needed yet.
 */
public class QuoteShippingOptionsUseCase {
    private static final long DEFAULT_DECLARED_VALUE_VND = 0L;

    private final CarrierGatewayPort carrierGateway;

    public QuoteShippingOptionsUseCase(CarrierGatewayPort carrierGateway) {
        this.carrierGateway = Objects.requireNonNull(carrierGateway, "carrierGateway is required");
    }

    public List<RateQuote> quoteOptions(ShippingAddress destination, Parcel parcel) {
        Objects.requireNonNull(destination, "destination is required");
        Objects.requireNonNull(parcel, "parcel is required");

        // Origin is a fixed warehouse for the marketplace seed. A real
        // implementation would resolve the seller's pickup address here.
        ShippingAddress origin = new ShippingAddress(
                "VNShop Warehouse",
                "+84900000000",
                "1 Warehouse St",
                null,
                "District 1",
                "HCMC"
        );

        // One quote per carrier; carriers map to buyer-facing tiers (GHN
        // returns standard pricing, GHTK is faster/express). Each carrier's
        // quote is independent — if one fails, surface the others rather
        // than failing the whole list.
        List<RateQuote> quotes = new ArrayList<>();
        for (CarrierCode carrier : CarrierCode.values()) {
            try {
                RateQuoteRequest request = new RateQuoteRequest(
                        carrier, origin, destination, parcel, DEFAULT_DECLARED_VALUE_VND);
                RateQuote quote = carrierGateway.quote(request);
                if (quote != null) quotes.add(quote);
            } catch (RuntimeException ignored) {
                // One carrier outage shouldn't strand the buyer at checkout.
                // Skip the failed carrier; return whatever the others gave us.
            }
        }
        return quotes;
    }
}
