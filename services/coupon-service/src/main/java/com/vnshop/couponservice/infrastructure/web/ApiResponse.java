package com.vnshop.couponservice.infrastructure.web;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire-format envelope shared with the rest of the platform. Mirrors
 * {@code com.vnshop.orderservice.infrastructure.web.ApiResponse} so the FE's
 * single envelope interceptor can parse every backend response uniformly.
 *
 * <p>Pre-existing CouponController endpoints returned bare records / lists,
 * which the FE rejected with "Invalid input" since the envelope check
 * unwraps {@code success/data} before validating the inner schema.
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        String errorCode,
        LocalDateTime timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "Success", data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new ApiResponse<>(false, message, null, errorCode, LocalDateTime.now());
    }

    @JsonProperty("code")
    public String code() {
        return errorCode;
    }
}
