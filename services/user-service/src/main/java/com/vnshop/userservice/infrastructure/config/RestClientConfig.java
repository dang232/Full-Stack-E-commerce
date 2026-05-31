package com.vnshop.userservice.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Single source of truth for the {@link RestClient.Builder} bean used by all
 * outbound HTTP integrations in user-service. The connect timeout pins the
 * underlying HttpClient; the read timeout is enforced via the request
 * factory. Both are short, prod-safe values so a slow downstream cannot
 * exhaust the connection pool. Resilience4j circuit breakers ride on top
 * and trip well before either timeout would saturate request threads.
 */
@Configuration
public class RestClientConfig {

    @Bean
    HttpClient sharedHttpClient(@Value("${vnshop.product-service.connect-timeout-ms:1000}") long connectTimeoutMs) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Bean
    JdkClientHttpRequestFactory jdkClientHttpRequestFactory(
            HttpClient sharedHttpClient,
            @Value("${vnshop.product-service.read-timeout-ms:2500}") long readTimeoutMs) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(sharedHttpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }

    @Bean
    RestClient.Builder restClientBuilder(JdkClientHttpRequestFactory factory) {
        return RestClient.builder().requestFactory(factory);
    }
}
