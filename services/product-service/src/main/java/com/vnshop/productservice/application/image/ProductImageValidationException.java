package com.vnshop.productservice.application.image;

import java.util.List;

public class ProductImageValidationException extends IllegalArgumentException {
    private final List<String> failures;

    public ProductImageValidationException(List<String> failures) {
        super("product image upload metadata failed validation");
        this.failures = List.copyOf(failures);
    }

    public List<String> failures() {
        return failures;
    }
}
