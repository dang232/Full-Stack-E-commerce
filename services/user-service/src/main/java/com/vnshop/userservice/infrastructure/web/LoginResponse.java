package com.vnshop.userservice.infrastructure.web;

public record LoginResponse(String accessToken, int accessExpiresIn) {}
