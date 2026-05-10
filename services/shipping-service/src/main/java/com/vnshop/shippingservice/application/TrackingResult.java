package com.vnshop.shippingservice.application;

import com.vnshop.shippingservice.domain.CarrierCode;
import com.vnshop.shippingservice.domain.TrackingStatus;

import java.time.Instant;

public record TrackingResult(
        CarrierCode carrierCode,
        String trackingCode,
        TrackingStatus status,
        String statusText,
        Instant updatedAt
) {
}
