package com.vnshop.shippingservice.domain.model;

public record Parcel(
        int weightGrams,
        int lengthCm,
        int widthCm,
        int heightCm) {
}
