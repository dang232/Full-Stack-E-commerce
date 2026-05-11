package com.vnshop.inventoryservice.application;

public record ReserveFlashSaleCommand(String productId, String buyerId, int quantity) {
}
