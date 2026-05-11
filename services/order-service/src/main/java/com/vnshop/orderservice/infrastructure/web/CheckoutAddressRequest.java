package com.vnshop.orderservice.infrastructure.web;

public record CheckoutAddressRequest(String street, String ward, String district, String city) {
}
