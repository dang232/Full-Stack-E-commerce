package com.vnshop.paymentservice.infrastructure.paypal;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.port.out.FxRatePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Thin REST wrapper for PayPal's v2 Checkout API. Two operations:
 *
 * <ul>
 *   <li>{@link #createOrder} — POSTs {@code /v2/checkout/orders}, returns the
 *       order id the FE Smart Buttons need.</li>
 *   <li>{@link #capture} — POSTs {@code /v2/checkout/orders/{id}/capture} and
 *       returns the capture id for ledger reconciliation.</li>
 * </ul>
 *
 * <p>OAuth: PayPal's REST API uses client_credentials. We mint a bearer per
 * call (no caching) — sandbox traffic is light enough that the extra round-trip
 * isn't worth a TTL bookkeeping bug. Production-rotation is left for follow-up.
 *
 * <p>Bean is gated on {@code payment.paypal.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "payment.paypal.enabled", havingValue = "true")
public class PayPalGateway {
    private static final Logger log = LoggerFactory.getLogger(PayPalGateway.class);

    private final PayPalProperties properties;
    private final FxRatePort fxRatePort;
    private final RestClient restClient;

    public PayPalGateway(PayPalProperties properties, FxRatePort fxRatePort, RestClient.Builder restClientBuilder) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.fxRatePort = Objects.requireNonNull(fxRatePort, "fxRatePort is required");
        requireNonBlank(properties.clientId(), "PayPal client id is required");
        requireNonBlank(properties.clientSecret(), "PayPal client secret is required");
        this.restClient = Objects.requireNonNull(restClientBuilder, "restClientBuilder is required")
                .baseUrl(properties.baseUrl())
                .build();
    }

    public PayPalProperties properties() {
        return properties;
    }

    /**
     * Build a CAPTURE-intent order in USD. Order metadata stashes the internal
     * paymentId so the {@code /capture} endpoint can resolve it without an
     * extra round-trip.
     */
    public PayPalOrder createOrder(Payment payment) {
        BigDecimal vndAmount = payment.amount();
        BigDecimal rate = fxRatePort.rate("VND", "USD");
        BigDecimal usdAmount = vndAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "reference_id", payment.paymentId().toString(),
                        "custom_id", payment.paymentId().toString(),
                        "description", "Order " + payment.orderId(),
                        "amount", Map.of(
                                "currency_code", "USD",
                                "value", usdAmount.toPlainString()))));

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/v2/checkout/orders")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null || response.get("id") == null) {
                throw new IllegalStateException("PayPal returned empty response");
            }
            return new PayPalOrder(
                    response.get("id").toString(),
                    response.get("status") != null ? response.get("status").toString() : "CREATED",
                    usdAmount,
                    "USD",
                    rate);
        } catch (RestClientResponseException ex) {
            log.warn("paypal-order-create-failed status={} body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new IllegalStateException("PayPal order create failed: " + ex.getStatusCode(), ex);
        }
    }

    /**
     * Capture an approved order. Returns the capture id (the per-payment
     * ledger reference). Idempotent on PayPal's side — calling twice returns
     * the same capture record on the second attempt.
     */
    public PayPalCapture capture(String paypalOrderId) {
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/v2/checkout/orders/{id}/capture", paypalOrderId)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(Map.class);
            return PayPalCapture.fromResponse(paypalOrderId, response);
        } catch (RestClientResponseException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            log.warn("paypal-capture-failed paypalOrderId={} status={} body={}",
                    paypalOrderId, status, ex.getResponseBodyAsString());
            throw new IllegalStateException("PayPal capture failed: " + ex.getStatusCode(), ex);
        }
    }

    private String bearer() {
        String credentials = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.clientSecret())
                        .getBytes(StandardCharsets.UTF_8));
        Map<?, ?> response = restClient.post()
                .uri("/v1/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("PayPal oauth token endpoint returned no access_token");
        }
        return "Bearer " + response.get("access_token");
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public record PayPalOrder(
            String paypalOrderId,
            String status,
            BigDecimal externalAmount,
            String externalCurrency,
            BigDecimal fxRate) {
    }

    public record PayPalCapture(
            String paypalOrderId,
            String captureId,
            String status) {
        public static PayPalCapture fromResponse(String paypalOrderId, Map<?, ?> response) {
            if (response == null) {
                throw new IllegalStateException("PayPal capture returned empty body");
            }
            String status = response.get("status") != null ? response.get("status").toString() : "UNKNOWN";
            String captureId = extractCaptureId(response);
            return new PayPalCapture(paypalOrderId, captureId, status);
        }

        private static String extractCaptureId(Map<?, ?> response) {
            Object purchaseUnits = response.get("purchase_units");
            if (purchaseUnits instanceof List<?> units && !units.isEmpty()
                    && units.get(0) instanceof Map<?, ?> firstUnit) {
                Object payments = firstUnit.get("payments");
                if (payments instanceof Map<?, ?> paymentsMap) {
                    Object captures = paymentsMap.get("captures");
                    if (captures instanceof List<?> captureList && !captureList.isEmpty()
                            && captureList.get(0) instanceof Map<?, ?> firstCapture) {
                        Object id = firstCapture.get("id");
                        if (id != null) return id.toString();
                    }
                }
            }
            return String.valueOf(response.get("id") == null ? "UNKNOWN" : response.get("id"));
        }
    }
}
