package com.vnshop.shippingservice.infrastructure.web;

import java.util.List;

public record TrackingResponse(
        String trackingCode,
        String carrier,
        String status,
        String estimatedDelivery,
        List<TrackingEvent> events
) {
    public record TrackingEvent(
            String at,
            String status,
            String location,
            String note
    ) {
    }
}
