package com.vnshop.orderservice.infrastructure.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.domain.port.out.CouponValidationPort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Wires the {@link CouponServiceHttpClient} declarative proxy and the
 * {@link CouponServiceAdapter} that converts JSON envelopes into
 * {@link CouponValidationPort} results. Mirrors the same shape as
 * {@code CartHttpClientConfig} so the two HTTP clients stay symmetric.
 */
@Configuration
public class CouponServiceHttpClientConfig {

    @Bean
    public CouponServiceHttpClient couponServiceHttpClient(
            @Value("${vnshop.coupon-service.base-url:http://coupon-service:8088}") String baseUrl,
            @Value("${vnshop.coupon-service.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${vnshop.coupon-service.read-timeout-ms:2000}") long readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(CouponServiceHttpClient.class);
    }

    @Bean
    public CouponValidationPort couponValidationPort(
            CouponServiceHttpClient httpClient, ObjectMapper objectMapper) {
        return new CouponServiceAdapter(httpClient, objectMapper);
    }
}
