package com.vnshop.reviewservice.infrastructure.web;

import com.vnshop.reviewservice.application.image.ReviewImageValidationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReviewImageUploadController.class)
public class ImageUploadExceptionHandler {
    @ExceptionHandler(ReviewImageValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse imageValidation(ReviewImageValidationException exception) {
        return new ErrorResponse(new ErrorBody("review_image_validation_failed", exception.getMessage(), exception.failures()));
    }

    public record ErrorResponse(ErrorBody error) {
    }

    public record ErrorBody(String code, String message, List<String> details) {
    }
}
