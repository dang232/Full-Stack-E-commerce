package com.vnshop.inventoryservice.application;

import java.math.BigDecimal;

public record ActiveFlashSaleCampaignView(
        String id,
        String productId,
        BigDecimal originalPrice,
        BigDecimal salePrice,
        int stockTotal,
        long stockRemaining,
        String endsAt) {
}
