package com.vnshop.orderservice.infrastructure.admin;

import java.math.BigDecimal;

public record TopItem(String id, String name, BigDecimal value) {
}
