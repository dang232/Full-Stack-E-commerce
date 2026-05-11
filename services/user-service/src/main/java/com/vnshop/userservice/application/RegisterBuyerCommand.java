package com.vnshop.userservice.application;

public record RegisterBuyerCommand(String keycloakId, String name, String phone, String avatarUrl) {
}
