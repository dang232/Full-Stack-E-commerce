package com.vnshop.orderservice.application;

import java.util.UUID;

public record ShipOrderCommand(UUID orderId, String sellerId, String carrier, String trackingNumber) {
}
