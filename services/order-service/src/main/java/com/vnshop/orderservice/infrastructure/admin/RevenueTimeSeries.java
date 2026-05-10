package com.vnshop.orderservice.infrastructure.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RevenueTimeSeries(List<Point> points) {
    public record Point(LocalDate date, BigDecimal revenue) {
    }
}
