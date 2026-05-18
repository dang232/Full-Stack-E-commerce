package com.vnshop.orderservice.application.shipping;

public record ShippingQuoteRequest(
        String street,
        String ward,
        String district,
        String city) {
}
