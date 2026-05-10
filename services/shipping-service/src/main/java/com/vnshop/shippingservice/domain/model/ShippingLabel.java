package com.vnshop.shippingservice.domain.model;

public record ShippingLabel(
        CarrierCode carrier,
        String orderId,
        String trackingCode,
        String labelUrl,
        long feeVnd) {
}
