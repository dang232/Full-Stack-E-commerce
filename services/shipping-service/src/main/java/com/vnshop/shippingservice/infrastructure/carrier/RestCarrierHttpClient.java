package com.vnshop.shippingservice.infrastructure.carrier;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnMissingBean(CarrierHttpClient.class)
@ConditionalOnProperty(name = "shipping.carrier.mode", havingValue = "live")
public class RestCarrierHttpClient implements CarrierHttpClient {
    private final RestClient.Builder restClientBuilder;

    @Override
    public <T> T post(String url, Map<String, String> headers, Object body, Class<T> responseType) {
        return restClientBuilder.build()
                .post()
                .uri(url)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                .body(body)
                .retrieve()
                .body(responseType);
    }

    @Override
    public <T> T get(String url, Map<String, String> headers, Class<T> responseType) {
        return restClientBuilder.build()
                .get()
                .uri(url)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                .retrieve()
                .body(responseType);
    }
}
