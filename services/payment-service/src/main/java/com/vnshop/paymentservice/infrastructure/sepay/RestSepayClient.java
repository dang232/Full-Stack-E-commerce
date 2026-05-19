package com.vnshop.paymentservice.infrastructure.sepay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(name = "payment.sepay.enabled", havingValue = "true")
public class RestSepayClient implements SepayClient {
    private final SepayProperties properties;
    private final RestClient restClient;

    public RestSepayClient(SepayProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.restClient = Objects.requireNonNull(restClientBuilder, "restClientBuilder is required")
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Override
    public SepayTransactionsResponse listTransactions(String sinceId) {
        StringBuilder uri = new StringBuilder("/transactions/list?account_id=")
                .append(properties.accountId());
        if (sinceId != null && !sinceId.isBlank()) {
            uri.append("&since_id=").append(sinceId);
        }
        SepayTransactionsResponse response = restClient.get()
                .uri(uri.toString())
                .header(HttpHeaders.AUTHORIZATION, "Apikey " + properties.apiKey())
                .retrieve()
                .body(SepayTransactionsResponse.class);
        if (response == null) {
            return new SepayTransactionsResponse(0, null, List.of());
        }
        return response;
    }
}
