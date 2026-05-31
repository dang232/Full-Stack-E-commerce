package com.vnshop.userservice.domain;

public class SellerNotFoundException extends RuntimeException {
    public SellerNotFoundException(String sellerId) {
        super("seller not found: " + sellerId);
    }
}
