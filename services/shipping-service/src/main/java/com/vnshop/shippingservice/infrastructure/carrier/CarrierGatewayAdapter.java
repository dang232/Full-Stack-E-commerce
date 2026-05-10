package com.vnshop.shippingservice.infrastructure.carrier;

import com.vnshop.shippingservice.domain.model.LabelRequest;
import com.vnshop.shippingservice.domain.model.RateQuote;
import com.vnshop.shippingservice.domain.model.RateQuoteRequest;
import com.vnshop.shippingservice.domain.model.ShippingLabel;
import com.vnshop.shippingservice.domain.model.TrackingInfo;
import com.vnshop.shippingservice.domain.model.TrackingRequest;

interface CarrierGatewayAdapter {
    RateQuote quote(RateQuoteRequest request);

    ShippingLabel createLabel(LabelRequest request);

    TrackingInfo track(TrackingRequest request);
}
