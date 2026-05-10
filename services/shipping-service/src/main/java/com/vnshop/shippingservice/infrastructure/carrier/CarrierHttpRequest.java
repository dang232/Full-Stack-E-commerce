package com.vnshop.shippingservice.infrastructure.carrier;

import java.util.Map;

public record CarrierHttpRequest(
        String method,
        String url,
        Map<String, String> headers,
        Object body
) {
}
