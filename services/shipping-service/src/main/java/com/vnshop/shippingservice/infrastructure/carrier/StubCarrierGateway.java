package com.vnshop.shippingservice.infrastructure.carrier;

import com.vnshop.shippingservice.domain.model.LabelRequest;
import com.vnshop.shippingservice.domain.model.RateQuote;
import com.vnshop.shippingservice.domain.model.RateQuoteRequest;
import com.vnshop.shippingservice.domain.model.ShippingLabel;
import com.vnshop.shippingservice.domain.model.TrackingInfo;
import com.vnshop.shippingservice.domain.model.TrackingRequest;
import com.vnshop.shippingservice.domain.port.out.CarrierGatewayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "shipping.carrier.mode", havingValue = "stub", matchIfMissing = true)
public class StubCarrierGateway implements CarrierGatewayPort {
    @Override
    public RateQuote quote(RateQuoteRequest request) {
        return new RateQuote(request.carrier(), 30_000L, "STUB_STANDARD", "2-4 days");
    }

    @Override
    public ShippingLabel createLabel(LabelRequest request) {
        return new ShippingLabel(request.carrier(), request.orderId(), "STUB-" + request.orderId(), null, 30_000L);
    }

    @Override
    public TrackingInfo track(TrackingRequest request) {
        return new TrackingInfo(request.carrier(), request.trackingCode(), "CREATED", "Stub shipment created", null);
    }
}
