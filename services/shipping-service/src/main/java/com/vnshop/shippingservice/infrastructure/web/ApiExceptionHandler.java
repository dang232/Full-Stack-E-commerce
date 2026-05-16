package com.vnshop.shippingservice.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> badRequest(IllegalArgumentException exception) {
        return ApiResponse.error(exception.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> missingParameter(MissingServletRequestParameterException exception) {
        return ApiResponse.error(
                "Required request parameter '" + exception.getParameterName() + "' is missing",
                "MISSING_PARAMETER");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return ApiResponse.error(
                "Invalid value for parameter '" + exception.getName() + "'",
                "INVALID_PARAMETER");
    }

    @ExceptionHandler(TrackingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> trackingNotFound(TrackingNotFoundException exception) {
        return ApiResponse.error(exception.getMessage(), "TRACKING_NOT_FOUND");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> internal(Exception exception) {
        return ApiResponse.error("An unexpected error occurred", "INTERNAL_SERVER_ERROR");
    }
}
