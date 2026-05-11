package com.vnshop.userservice.application;

public record UpsertBuyerProfileCommand(String keycloakId, String name, String phone, String avatarUrl) {
}
