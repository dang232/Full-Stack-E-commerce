package com.vnshop.shippingservice.application;

import com.vnshop.shippingservice.domain.model.CarrierCode;
import com.vnshop.shippingservice.domain.model.LabelRequest;
import com.vnshop.shippingservice.domain.model.Parcel;
import com.vnshop.shippingservice.domain.model.RateQuote;
import com.vnshop.shippingservice.domain.model.RateQuoteRequest;
import com.vnshop.shippingservice.domain.model.ShippingAddress;
import com.vnshop.shippingservice.domain.model.ShippingLabel;
import com.vnshop.shippingservice.domain.model.TrackingInfo;
import com.vnshop.shippingservice.domain.model.TrackingRequest;
import com.vnshop.shippingservice.domain.port.out.CarrierGatewayPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteShippingOptionsUseCaseTest {

    private final ShippingAddress destination = new ShippingAddress(
            "Buyer", "+84900000001", "1 Test Way", null, "Q1", "HCMC");
    private final Parcel parcel = new Parcel(1000, 30, 20, 10);

    @Test
    void quoteOptions_returnsOnePerCarrier() {
        QuoteShippingOptionsUseCase useCase = new QuoteShippingOptionsUseCase(new HappyGateway());
        List<RateQuote> result = useCase.quoteOptions(destination, parcel);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(RateQuote::carrier)
                .containsExactlyInAnyOrder(CarrierCode.GHN, CarrierCode.GHTK);
    }

    @Test
    void quoteOptions_singleCarrierFailure_returnsRemainingOptions() {
        // GHN fails, GHTK succeeds. The buyer should still get the GHTK option
        // — never empty the list because one carrier had a hiccup.
        QuoteShippingOptionsUseCase useCase = new QuoteShippingOptionsUseCase(new GhnFailsGateway());
        List<RateQuote> result = useCase.quoteOptions(destination, parcel);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).carrier()).isEqualTo(CarrierCode.GHTK);
    }

    @Test
    void quoteOptions_allCarriersFail_returnsEmpty() {
        QuoteShippingOptionsUseCase useCase = new QuoteShippingOptionsUseCase(new AllFailsGateway());
        assertThat(useCase.quoteOptions(destination, parcel)).isEmpty();
    }

    @Test
    void quoteOptions_carrierReturnsNull_skipped() {
        QuoteShippingOptionsUseCase useCase = new QuoteShippingOptionsUseCase(new NullForOneGateway());
        List<RateQuote> result = useCase.quoteOptions(destination, parcel);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).carrier()).isEqualTo(CarrierCode.GHTK);
    }

    private static final class HappyGateway implements CarrierGatewayPort {
        @Override public RateQuote quote(RateQuoteRequest request) {
            return new RateQuote(request.carrier(), 30_000L, "STANDARD", "3-5 days");
        }
        @Override public ShippingLabel createLabel(LabelRequest r) { return null; }
        @Override public TrackingInfo track(TrackingRequest r) { return null; }
    }

    private static final class GhnFailsGateway implements CarrierGatewayPort {
        @Override public RateQuote quote(RateQuoteRequest request) {
            if (request.carrier() == CarrierCode.GHN) throw new RuntimeException("GHN down");
            return new RateQuote(request.carrier(), 45_000L, "EXPRESS", "1-2 days");
        }
        @Override public ShippingLabel createLabel(LabelRequest r) { return null; }
        @Override public TrackingInfo track(TrackingRequest r) { return null; }
    }

    private static final class AllFailsGateway implements CarrierGatewayPort {
        @Override public RateQuote quote(RateQuoteRequest request) { throw new RuntimeException("all carriers down"); }
        @Override public ShippingLabel createLabel(LabelRequest r) { return null; }
        @Override public TrackingInfo track(TrackingRequest r) { return null; }
    }

    private static final class NullForOneGateway implements CarrierGatewayPort {
        @Override public RateQuote quote(RateQuoteRequest request) {
            if (request.carrier() == CarrierCode.GHN) return null;
            return new RateQuote(request.carrier(), 45_000L, "EXPRESS", "1-2 days");
        }
        @Override public ShippingLabel createLabel(LabelRequest r) { return null; }
        @Override public TrackingInfo track(TrackingRequest r) { return null; }
    }
}
