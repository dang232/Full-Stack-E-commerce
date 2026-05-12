package com.vnshop.orderservice.infrastructure.web;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    String errorCode,
    LocalDateTime timestamp
) {
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
