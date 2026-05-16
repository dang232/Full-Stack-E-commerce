package com.vnshop.paymentservice.infrastructure.web;

import com.vnshop.paymentservice.application.IdempotencyKeyConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> badRequest(IllegalArgumentException exception) {
        return ApiResponse.error(exception.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<Void> idempotencyKeyConflict(IdempotencyKeyConflictException exception) {
        return ApiResponse.error(exception.getMessage(), "IDEMPOTENCY_KEY_CONFLICT");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> serviceUnavailable(IllegalStateException exception) {
        return ApiResponse.error(exception.getMessage(), "SERVICE_UNAVAILABLE");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> internal(Exception exception) {
        return ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR");
    }
}
