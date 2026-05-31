package com.vnshop.paymentservice.infrastructure.order;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Wires the {@link OrderHttpClient} declarative proxy. Timeout configuration
 * that previously lived in {@link OrderCatalogAdapter}'s constructor is now
 * owned here so the adapter itself is free of infrastructure concerns.
 */
@Configuration
public class OrderHttpClientConfig {

    @Bean
    public OrderHttpClient orderHttpClient(
            @Value("${vnshop.order-service.base-url:http://order-service:8091}") String baseUrl,
            @Value("${vnshop.order-service.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${vnshop.order-service.read-timeout-ms:2000}") long readTimeoutMs) {

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
                .createClient(OrderHttpClient.class);
    }
}
