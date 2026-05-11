package com.vnshop.userservice.application;

public record RegisterSellerCommand(String keycloakId, String shopName, String bankName, String bankAccount) {
}
