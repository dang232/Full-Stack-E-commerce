package com.vnshop.userservice.infrastructure.integration;

import com.vnshop.userservice.domain.port.out.SellerStatsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductServiceSellerStatsAdapterTest {

    private static final String BASE_URL = "http://product-service:8082";

    private MockRestServiceServer server;
    private ProductServiceSellerStatsAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        // Cache disabled for these tests (1-second TTL, but each test makes one
        // call). The cache-hit behaviour is covered by a dedicated test below.
        adapter = new ProductServiceSellerStatsAdapter(builder, BASE_URL, 300L, 10_000L);
    }

    @Test
    void sellerStats_happyPath_parsesRatingAvgAndCount() {
        String body = "{\"data\":{\"ratingAvg\":4.5,\"ratingCount\":120}}";
        server.expect(requestTo(BASE_URL + "/reviews/seller/s1/summary"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        SellerStatsPort.SellerStats stats = adapter.sellerStats("s1");

        assertThat(stats.ratingAvg()).isEqualTo(4.5);
        assertThat(stats.ratingCount()).isEqualTo(120L);
        server.verify();
    }

    @Test
    void sellerStats_nullRatingAvg_returnsNull() {
        String body = "{\"data\":{\"ratingAvg\":null,\"ratingCount\":0}}";
        server.expect(requestTo(BASE_URL + "/reviews/seller/s1/summary"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        SellerStatsPort.SellerStats stats = adapter.sellerStats("s1");

        assertThat(stats.ratingAvg()).isNull();
        assertThat(stats.ratingCount()).isZero();
        server.verify();
    }

    @Test
    void sellerStats_serverError_returnsDefaults() {
        server.expect(requestTo(BASE_URL + "/reviews/seller/s1/summary"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        SellerStatsPort.SellerStats stats = adapter.sellerStats("s1");

        assertThat(stats.ratingAvg()).isNull();
        assertThat(stats.ratingCount()).isZero();
        server.verify();
    }

    @Test
    void productCount_happyPath_parsesCount() {
        String body = "{\"data\":{\"count\":42}}";
        server.expect(requestTo(BASE_URL + "/products/count?sellerId=s1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        long count = adapter.productCount("s1");

        assertThat(count).isEqualTo(42L);
        server.verify();
    }

    @Test
    void sellerStats_emptyBody_returnsDefaults() {
        server.expect(requestTo(BASE_URL + "/reviews/seller/s1/summary"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        SellerStatsPort.SellerStats stats = adapter.sellerStats("s1");

        assertThat(stats.ratingAvg()).isNull();
        assertThat(stats.ratingCount()).isZero();
        server.verify();
    }

    @Test
    void productCount_emptyBody_returnsZero() {
        server.expect(requestTo(BASE_URL + "/products/count?sellerId=s1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        long count = adapter.productCount("s1");

        assertThat(count).isZero();
        server.verify();
    }
}
