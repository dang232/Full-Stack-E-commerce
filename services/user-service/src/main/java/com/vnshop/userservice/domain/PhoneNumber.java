package com.vnshop.userservice.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record PhoneNumber(String value) {
    private static final Pattern VIETNAM_E164_PATTERN = Pattern.compile("^\\+84\\d{9,10}$");

    public PhoneNumber {
        Objects.requireNonNull(value, "phone number is required");
        if (!VIETNAM_E164_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("phone number must be E.164 format with +84 prefix");
        }
    }
}
