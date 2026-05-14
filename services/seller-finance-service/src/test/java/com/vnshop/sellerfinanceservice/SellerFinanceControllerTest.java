package com.vnshop.sellerfinanceservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.sellerfinanceservice.domain.Payout;
import com.vnshop.sellerfinanceservice.domain.PayoutStatus;
import com.vnshop.sellerfinanceservice.domain.SellerWallet;
import com.vnshop.sellerfinanceservice.domain.port.out.PayoutRepositoryPort;
import com.vnshop.sellerfinanceservice.domain.port.out.SellerWalletRepositoryPort;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
})
class SellerFinanceControllerTest {

    private static final String SELLER_ID = "seller-1";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @MockitoBean
    private ObjectMapper contextObjectMapper;

    @MockitoBean
    private SellerWalletRepositoryPort sellerWalletRepositoryPort;

    @MockitoBean
    private PayoutRepositoryPort payoutRepositoryPort;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void configureJwt() {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", SELLER_ID)
        );
        when(jwtDecoder.decode("token")).thenReturn(jwt);
    }

    @Test
    void getPayoutsReturnsList() throws Exception {
        Payout payout = new Payout(UUID.randomUUID(), SELLER_ID, new BigDecimal("125.50"), PayoutStatus.PENDING, Instant.parse("2026-05-14T00:00:00Z"));
        when(payoutRepositoryPort.findBySellerId(SELLER_ID)).thenReturn(List.of(payout));

        HttpRequest request = authorizedRequest("/sellers/me/finance/payouts")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("success").asBoolean()).isTrue();
        assertThat(body.get("data")).hasSize(1);
        assertThat(body.get("data").get(0).get("sellerId").asText()).isEqualTo(SELLER_ID);
        assertThat(body.get("data").get(0).get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void requestPayoutReturnsValidResponse() throws Exception {
        SellerWallet wallet = new SellerWallet(SELLER_ID, new BigDecimal("200.00"), BigDecimal.ZERO, new BigDecimal("200.00"), null);
        Payout savedPayout = new Payout(UUID.randomUUID(), SELLER_ID, new BigDecimal("125.50"), PayoutStatus.PENDING, Instant.parse("2026-05-14T00:00:00Z"));
        when(sellerWalletRepositoryPort.findBySellerId(SELLER_ID)).thenReturn(Optional.of(wallet));
        when(payoutRepositoryPort.save(any(Payout.class))).thenReturn(savedPayout);

        HttpRequest request = authorizedRequest("/sellers/me/finance/payouts")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":125.50}"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = objectMapper.readTree(response.body());
        JsonNode data = body.get("data");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(body.get("success").asBoolean()).isTrue();
        assertThat(data.get("payoutId").asText()).isNotBlank();
        assertThat(data.get("sellerId").asText()).isEqualTo(SELLER_ID);
        assertThat(data.get("amount").decimalValue()).isEqualByComparingTo("125.50");
        assertThat(data.get("status").asText()).isEqualTo("PENDING");
    }

    private HttpRequest.Builder authorizedRequest(String path) {
        return HttpRequest.newBuilder(url(path))
                .header("Authorization", "Bearer token");
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
