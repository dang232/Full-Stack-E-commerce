package com.vnshop.shippingservice.domain.model;

public record TrackingInfo(
        CarrierCode carrier,
        String trackingCode,
        String status,
        String statusDescription,
        String updatedAt) {
}
