package com.vnshop.shippingservice.application;

import com.vnshop.shippingservice.domain.model.RateQuote;
import com.vnshop.shippingservice.domain.model.RateQuoteRequest;
import com.vnshop.shippingservice.domain.port.out.CarrierGatewayPort;

import java.util.Objects;

public class ShippingRateCalculator {
    private final CarrierGatewayPort carrierGateway;

    public ShippingRateCalculator(CarrierGatewayPort carrierGateway) {
        this.carrierGateway = Objects.requireNonNull(carrierGateway, "carrierGateway is required");
    }

    public RateQuote quote(RateQuoteRequest request) {
        return carrierGateway.quote(request);
    }
}
