package com.vnshop.shippingservice.application;

import com.vnshop.shippingservice.domain.CarrierCode;
import com.vnshop.shippingservice.domain.Money;

import java.time.Instant;

public record RateQuoteResult(
        CarrierCode carrierCode,
        String serviceCode,
        String serviceName,
        Money shippingFee,
        Instant estimatedDeliveryAt,
        String rawCarrierRef
) {
}
