package com.vnshop.orderservice.infrastructure.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.application.shipping.ShippingOption;
import com.vnshop.orderservice.application.shipping.ShippingQuotePort;
import com.vnshop.orderservice.application.shipping.ShippingQuoteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Outbound adapter to shipping-service /shipping/rate-quotes. Mirrors the
 * resilience pattern from user-service's ProductServiceSellerStatsAdapter:
 * pinned timeouts via JdkClientHttpRequestFactory, graceful degradation on
 * any failure (returns an empty list so the controller can surface its
 * static fallback rather than 500 the buyer at checkout).
 *
 * <p>No circuit breaker here — the controller's degradation path already
 * rides on resilience4j at the gateway. Adding another breaker in-process
 * would double-count failures against the service-level budget.
 */
@Component
public class ShippingServiceQuoteAdapter implements ShippingQuotePort {

    private static final Logger log = LoggerFactory.getLogger(ShippingServiceQuoteAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public ShippingServiceQuoteAdapter(
            @Value("${vnshop.shipping-service.uri:http://shipping-service:8093}") String shippingServiceUri,
            @Value("${vnshop.shipping-service.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${vnshop.shipping-service.read-timeout-ms:2500}") long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(shippingServiceUri)
                .requestFactory(factory)
                .build();
    }

    @Override
    public List<ShippingOption> quote(ShippingQuoteRequest request) {
        try {
            Map<String, Object> body = buildRequestBody(request);
            String response = restClient.post()
                    .uri("/shipping/rate-quotes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) return List.of();
            JsonNode options = MAPPER.readTree(response).path("data").path("options");
            List<ShippingOption> out = new ArrayList<>();
            for (JsonNode option : options) {
                String serviceCode = option.path("serviceCode").asText("STANDARD");
                long feeVnd = option.path("feeVnd").asLong(0L);
                String eta = option.path("estimatedDeliveryTime").asText("3-5 days");
                out.add(new ShippingOption(serviceCode, BigDecimal.valueOf(feeVnd), eta));
            }
            return out;
        } catch (Exception e) {
            log.warn("shipping-service /rate-quotes failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static Map<String, Object> buildRequestBody(ShippingQuoteRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("street", request.street());
        body.put("ward", request.ward());
        body.put("district", request.district());
        body.put("province", request.city());
        // Default parcel for now; a future enhancement could pass real
        // weight/dimensions sourced from cart line items + product master.
        return body;
    }
}
