package com.vnshop.userservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressTest {

    @Test
    void validAddress_createsSuccessfully() {
        Address a = new Address("123 Main St", "Ward 1", "District 1", "Hanoi", true);
        assertThat(a.street()).isEqualTo("123 Main St");
        assertThat(a.isDefault()).isTrue();
    }

    @Test
    void nullStreet_throws() {
        assertThatThrownBy(() -> new Address(null, "Ward", "District", "City", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("street");
    }

    @Test
    void blankStreet_throws() {
        assertThatThrownBy(() -> new Address("  ", "Ward", "District", "City", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("street");
    }

    @Test
    void nullDistrict_throws() {
        assertThatThrownBy(() -> new Address("Street", "Ward", null, "City", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("district");
    }

    @Test
    void blankDistrict_throws() {
        assertThatThrownBy(() -> new Address("Street", "Ward", "", "City", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("district");
    }

    @Test
    void nullCity_throws() {
        assertThatThrownBy(() -> new Address("Street", "Ward", "District", null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("city");
    }

    @Test
    void blankCity_throws() {
        assertThatThrownBy(() -> new Address("Street", "Ward", "District", "  ", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("city");
    }

    @Test
    void nullWard_isAllowed() {
        Address a = new Address("Street", null, "District", "City", false);
        assertThat(a.ward()).isNull();
    }
}
