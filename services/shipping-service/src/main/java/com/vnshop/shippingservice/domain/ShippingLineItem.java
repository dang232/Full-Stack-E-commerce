package com.vnshop.shippingservice.domain;

import java.math.BigDecimal;

public record ShippingLineItem(String name, int quantity, BigDecimal declaredValue) {
}
