package com.vnshop.orderservice.infrastructure.product;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Wires the {@link ProductHttpClient} declarative proxy. Timeout configuration
 * that previously lived in {@link ProductCatalogAdapter}'s constructor is now
 * owned here so the adapter itself is free of infrastructure concerns.
 */
@Configuration
public class ProductHttpClientConfig {

    @Bean
    public ProductHttpClient productHttpClient(
            @Value("${vnshop.product-service.base-url:http://product-service:8082}") String baseUrl,
            @Value("${vnshop.product-service.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${vnshop.product-service.read-timeout-ms:2000}") long readTimeoutMs) {

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
                .createClient(ProductHttpClient.class);
    }
}
