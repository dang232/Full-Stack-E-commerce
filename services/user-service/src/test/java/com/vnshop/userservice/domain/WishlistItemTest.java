package com.vnshop.userservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WishlistItemTest {

    @Test
    void validItem_createsSuccessfully() {
        Instant now = Instant.now();
        WishlistItem item = new WishlistItem("kc-1", "prod-1", now);
        assertThat(item.keycloakId()).isEqualTo("kc-1");
        assertThat(item.productId()).isEqualTo("prod-1");
        assertThat(item.createdAt()).isEqualTo(now);
    }

    @Test
    void nullKeycloakId_throws() {
        assertThatThrownBy(() -> new WishlistItem(null, "prod-1", Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankKeycloakId_throws() {
        assertThatThrownBy(() -> new WishlistItem("  ", "prod-1", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keycloakId");
    }

    @Test
    void nullProductId_throws() {
        assertThatThrownBy(() -> new WishlistItem("kc-1", null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankProductId_throws() {
        assertThatThrownBy(() -> new WishlistItem("kc-1", "", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void nullCreatedAt_throws() {
        assertThatThrownBy(() -> new WishlistItem("kc-1", "prod-1", null))
                .isInstanceOf(NullPointerException.class);
    }
}
