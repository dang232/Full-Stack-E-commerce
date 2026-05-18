package com.vnshop.orderservice.application.shipping;

import java.math.BigDecimal;

public record ShippingOption(String method, BigDecimal cost, String estimate) {
}
