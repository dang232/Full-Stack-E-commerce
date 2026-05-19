package com.vnshop.shippingservice.infrastructure.web;

public record RateQuoteResponse(
        String carrier,
        String serviceCode,
        long feeVnd,
        String estimatedDeliveryTime) {}
