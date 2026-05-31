package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.PublicSellerView;
import com.vnshop.userservice.application.PublicSellersPage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicSellerResponseTest {

    private static final Instant JOINED = Instant.parse("2024-03-01T00:00:00Z");

    private PublicSellerView view(String id) {
        return new PublicSellerView(id, "Shop", "desc", "http://logo", "http://banner",
                JOINED, "STANDARD", 4.2, 10L, 5L);
    }

    @Test
    void fromView_mapsAllFields() {
        PublicSellerResponse resp = PublicSellerResponse.fromView(view("s1"));

        assertThat(resp.id()).isEqualTo("s1");
        assertThat(resp.shopName()).isEqualTo("Shop");
        assertThat(resp.description()).isEqualTo("desc");
        assertThat(resp.logoUrl()).isEqualTo("http://logo");
        assertThat(resp.bannerUrl()).isEqualTo("http://banner");
        assertThat(resp.joinedAt()).isEqualTo(JOINED);
        assertThat(resp.tier()).isEqualTo("STANDARD");
        assertThat(resp.ratingAvg()).isEqualTo(4.2);
        assertThat(resp.ratingCount()).isEqualTo(10L);
        assertThat(resp.totalProducts()).isEqualTo(5L);
    }

    @Test
    void fromView_nullRatingAvg_preserved() {
        PublicSellerView v = new PublicSellerView("s1", "Shop", null, null, null,
                JOINED, "STANDARD", null, 0L, 0L);
        PublicSellerResponse resp = PublicSellerResponse.fromView(v);
        assertThat(resp.ratingAvg()).isNull();
    }

    @Test
    void fromPage_mapsContentAndPagination() {
        PublicSellersPage page = new PublicSellersPage(
                List.of(view("s1"), view("s2")), 0, 20, 2L, 1);

        PublicSellersPageResponse resp = PublicSellersPageResponse.fromPage(page);

        assertThat(resp.content()).hasSize(2);
        assertThat(resp.page()).isZero();
        assertThat(resp.size()).isEqualTo(20);
        assertThat(resp.totalElements()).isEqualTo(2L);
        assertThat(resp.totalPages()).isEqualTo(1);
    }

    @Test
    void fromPage_emptyContent() {
        PublicSellersPage page = new PublicSellersPage(List.of(), 0, 20, 0L, 0);
        PublicSellersPageResponse resp = PublicSellersPageResponse.fromPage(page);
        assertThat(resp.content()).isEmpty();
    }
}
