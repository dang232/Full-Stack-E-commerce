package com.vnshop.userservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberTest {

    @Test
    void validVietnameseNumber_createsSuccessfully() {
        PhoneNumber p = new PhoneNumber("+84912345678");
        assertThat(p.value()).isEqualTo("+84912345678");
    }

    @Test
    void nullValue_throwsNullPointer() {
        assertThatThrownBy(() -> new PhoneNumber(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void invalidFormat_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PhoneNumber("0912345678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("E.164");
    }

    @Test
    void wrongCountryCode_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PhoneNumber("+1234567890"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tenDigitVietnameseNumber_createsSuccessfully() {
        PhoneNumber p = new PhoneNumber("+849123456789");
        assertThat(p.value()).startsWith("+84");
    }
}
