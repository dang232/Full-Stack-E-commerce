package com.vnshop.orderservice.application;

import java.util.UUID;

public record CancelOrderCommand(UUID id, String buyerId) {
}
