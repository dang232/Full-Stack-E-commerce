package com.vnshop.shippingservice.application;

import com.vnshop.shippingservice.domain.CarrierCode;
import com.vnshop.shippingservice.domain.Money;
import com.vnshop.shippingservice.domain.Parcel;
import com.vnshop.shippingservice.domain.ShippingAddress;

public record RateQuoteCommand(
        CarrierCode carrierCode,
        ShippingAddress origin,
        ShippingAddress destination,
        Parcel parcel,
        Money declaredValue
) {
}
