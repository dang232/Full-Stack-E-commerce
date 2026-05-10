package com.vnshop.shippingservice.domain.model;

public record RateQuote(
        CarrierCode carrier,
        long totalFeeVnd,
        String serviceCode,
        String estimatedDeliveryTime) {
}
