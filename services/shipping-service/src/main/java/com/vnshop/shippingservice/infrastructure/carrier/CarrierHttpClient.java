package com.vnshop.shippingservice.infrastructure.carrier;

import java.util.Map;

public interface CarrierHttpClient {
    <T> T post(String url, Map<String, String> headers, Object body, Class<T> responseType);

    <T> T get(String url, Map<String, String> headers, Class<T> responseType);
}
