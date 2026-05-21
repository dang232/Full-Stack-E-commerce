package com.vnshop.orderservice.infrastructure.cart;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Wires the {@link CartHttpClient} declarative proxy. Timeout configuration
 * that previously lived in {@link CartServiceAdapter}'s constructor is now
 * owned here so the adapter itself is free of infrastructure concerns.
 */
@Configuration
public class CartHttpClientConfig {

    @Bean
    public CartHttpClient cartHttpClient(
            @Value("${vnshop.cart-service.base-url:http://cart-service:8084}") String baseUrl,
            @Value("${vnshop.cart-service.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${vnshop.cart-service.read-timeout-ms:2000}") long readTimeoutMs) {

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
                .createClient(CartHttpClient.class);
    }
}
