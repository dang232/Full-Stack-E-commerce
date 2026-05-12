package com.vnshop.productservice.application.review.image;

import java.util.List;

public class ReviewImageValidationException extends IllegalArgumentException {
    private final List<String> failures;

    public ReviewImageValidationException(List<String> failures) {
        super("review image upload metadata failed validation");
        this.failures = List.copyOf(failures);
    }

    public List<String> failures() {
        return failures;
    }
}
