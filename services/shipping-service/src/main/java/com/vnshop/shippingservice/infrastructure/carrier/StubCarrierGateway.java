package com.vnshop.shippingservice.infrastructure.carrier;

import com.vnshop.shippingservice.domain.exception.CarrierTrackingNotFoundException;
import com.vnshop.shippingservice.domain.model.LabelRequest;
import com.vnshop.shippingservice.domain.model.RateQuote;
import com.vnshop.shippingservice.domain.model.RateQuoteRequest;
import com.vnshop.shippingservice.domain.model.ShippingLabel;
import com.vnshop.shippingservice.domain.model.TrackingEvent;
import com.vnshop.shippingservice.domain.model.TrackingInfo;
import com.vnshop.shippingservice.domain.model.TrackingRequest;
import com.vnshop.shippingservice.domain.port.out.CarrierGatewayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

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
        // Test hook: tracking codes that start with MISSING- model the real
        // carrier "we have never seen this code" response so the use case can
        // surface a 404 to the buyer. Anything else returns a normal stub.
        String code = request.trackingCode();
        if (code != null && code.startsWith("MISSING-")) {
            throw new CarrierTrackingNotFoundException("Stub carrier has no record of " + code);
        }
        // Synthetic timeline so dev/FE work isn't stuck on an empty list.
        // Vietnamese carrier vocabulary keeps it realistic for the FE
        // TrackingModal which renders these strings directly.
        List<TrackingEvent> events = List.of(
                new TrackingEvent("2026-05-15T08:00:00Z", "PICKED_UP", "Kho HCM", "Đã nhận hàng từ người bán"),
                new TrackingEvent("2026-05-16T09:30:00Z", "IN_TRANSIT", "Trung chuyển Đà Nẵng", "Đang vận chuyển"),
                new TrackingEvent("2026-05-17T07:15:00Z", "OUT_FOR_DELIVERY", "Bưu cục Hà Nội", "Đang giao hàng")
        );
        return new TrackingInfo(request.carrier(), code, "OUT_FOR_DELIVERY", "Stub shipment in transit", "2026-05-17T07:15:00Z", events);
    }
}
