package com.vnshop.shippingservice.application;

import com.vnshop.shippingservice.domain.exception.CarrierTrackingNotFoundException;
import com.vnshop.shippingservice.domain.model.CarrierCode;
import com.vnshop.shippingservice.domain.model.TrackingInfo;
import com.vnshop.shippingservice.domain.model.TrackingRequest;
import com.vnshop.shippingservice.domain.port.out.CarrierGatewayPort;

import java.util.Objects;
import java.util.Optional;

public class GetTrackingUseCase {
    private final CarrierGatewayPort carrierGateway;

    public GetTrackingUseCase(CarrierGatewayPort carrierGateway) {
        this.carrierGateway = Objects.requireNonNull(carrierGateway, "carrierGateway is required");
    }

    public Optional<TrackingInfo> get(CarrierCode carrier, String trackingCode) {
        Objects.requireNonNull(carrier, "carrier is required");
        if (trackingCode == null || trackingCode.isBlank()) {
            throw new IllegalArgumentException("trackingCode must not be blank");
        }
        try {
            TrackingInfo info = carrierGateway.track(new TrackingRequest(carrier, trackingCode));
            return Optional.ofNullable(info);
        } catch (CarrierTrackingNotFoundException notFound) {
            // Carrier confirmed the tracking code does not exist; controller maps to 404.
            return Optional.empty();
        }
        // Other carrier failures (timeout, 5xx, network) propagate so the
        // exception handler maps them to 500 INTERNAL_SERVER_ERROR rather
        // than misleading the buyer with "tracking code doesn't exist".
    }
}

