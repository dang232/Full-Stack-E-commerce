package com.vnshop.paymentservice.infrastructure.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Component
@ConditionalOnProperty(name = "payment.mode", havingValue = "live")
public class RestMomoClient implements MomoClient {
    private final MomoProperties properties;
    private final RestClient restClient;

    public RestMomoClient(MomoProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.restClient = Objects.requireNonNull(restClientBuilder, "restClientBuilder is required").build();
    }

    @Override
    public MomoCreateResponse create(MomoCreateRequest request) {
        return restClient.post()
                .uri(properties.createEndpoint())
                .body(request)
                .retrieve()
                .body(MomoCreateResponse.class);
    }

    @Override
    public MomoQueryDrResponse query(MomoQueryDrRequest request) {
        return restClient.post()
                .uri(properties.queryDrEndpoint())
                .body(request)
                .retrieve()
                .body(MomoQueryDrResponse.class);
    }
}
