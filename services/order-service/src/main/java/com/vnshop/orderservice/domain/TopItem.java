package com.vnshop.orderservice.domain;

import java.math.BigDecimal;

public record TopItem(String id, String name, BigDecimal value) {
}
