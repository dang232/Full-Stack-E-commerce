package com.vnshop.shippingservice.application;

import com.vnshop.shippingservice.domain.CarrierCode;

public record CreateLabelResult(
        CarrierCode carrierCode,
        String orderId,
        String carrierOrderCode,
        String trackingCode,
        String labelUrl
) {
}
