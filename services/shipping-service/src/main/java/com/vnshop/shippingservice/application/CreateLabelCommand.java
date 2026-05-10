package com.vnshop.shippingservice.application;

import com.vnshop.shippingservice.domain.CarrierCode;
import com.vnshop.shippingservice.domain.Money;
import com.vnshop.shippingservice.domain.Parcel;
import com.vnshop.shippingservice.domain.ShippingAddress;
import com.vnshop.shippingservice.domain.ShippingLineItem;

import java.util.List;

public record CreateLabelCommand(
        CarrierCode carrierCode,
        String orderId,
        ShippingAddress origin,
        ShippingAddress destination,
        Parcel parcel,
        Money codAmount,
        Money declaredValue,
        List<ShippingLineItem> items
) {
}
