package com.vnshop.shippingservice.domain.model;

public record RateQuoteRequest(
        CarrierCode carrier,
        ShippingAddress fromAddress,
        ShippingAddress toAddress,
        Parcel parcel,
        long declaredValueVnd) {
}
