package com.vnshop.shippingservice.domain.model;

public record TrackingRequest(
        CarrierCode carrier,
        String trackingCode) {
}
