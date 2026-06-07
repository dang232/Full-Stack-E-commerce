package com.vnshop.orderservice.application.tax;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of a tax calculation: the applied rate, per-item breakdown, and total tax in VND.
 */
public record TaxResult(
        BigDecimal totalTax,
        BigDecimal appliedRate,
        List<LineItemTax> lineItems
) {
    public record LineItemTax(
            String productId,
            String variantSku,
            BigDecimal rate,
            long taxAmount
    ) {}
}
