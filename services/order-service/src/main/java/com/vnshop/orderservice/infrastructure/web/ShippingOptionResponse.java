package com.vnshop.orderservice.infrastructure.web;

import java.math.BigDecimal;

public record ShippingOptionResponse(String method, BigDecimal cost, String estimate) {
}
