package com.vnshop.orderservice.application.tax;

import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.OrderItem;
import com.vnshop.orderservice.domain.port.out.TaxRateLookupPort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Calculates Vietnam VAT for a list of order items.
 *
 * <p>Rules:
 * <ul>
 *   <li>Rate is looked up from the {@code tax_rates} table by category code.
 *   <li>Items without a matching category default to the STANDARD (10%) rate.
 *   <li>Per-item tax: {@code roundTo1000(subtotal × rate)}.
 *   <li>VND rounding: nearest 1,000₫ — {@code Math.round(amount / 1000.0) * 1000}.
 * </ul>
 */
public class TaxCalculationService {

    static final String STANDARD_CATEGORY = "STANDARD";
    private static final BigDecimal DEFAULT_RATE = new BigDecimal("0.10");

    private final TaxRateLookupPort taxRateLookupPort;

    public TaxCalculationService(TaxRateLookupPort taxRateLookupPort) {
        this.taxRateLookupPort = Objects.requireNonNull(taxRateLookupPort, "taxRateLookupPort is required");
    }

    /**
     * Calculates tax for all items as of today's date.
     * All items are treated as STANDARD category — pass items with a
     * {@code categoryCode} field when per-category rates are needed.
     */
    public TaxResult calculate(List<OrderItem> items) {
        return calculate(items, STANDARD_CATEGORY, LocalDate.now());
    }

    /**
     * Calculates tax using the given category code and reference date.
     * Falls back to {@link #DEFAULT_RATE} when no rate row is found.
     */
    public TaxResult calculate(List<OrderItem> items, String categoryCode, LocalDate asOf) {
        Objects.requireNonNull(items, "items is required");
        if (items.isEmpty()) {
            return new TaxResult(BigDecimal.ZERO, DEFAULT_RATE, List.of());
        }

        BigDecimal rate = taxRateLookupPort.findRate(categoryCode, asOf)
                .orElse(DEFAULT_RATE);

        List<TaxResult.LineItemTax> lineItemTaxes = items.stream()
                .map(item -> {
                    BigDecimal subtotal = item.totalPrice().amount();
                    long taxAmount = roundTo1000(subtotal.multiply(rate));
                    return new TaxResult.LineItemTax(item.productId(), item.variantSku(), rate, taxAmount);
                })
                .toList();

        long totalTax = lineItemTaxes.stream()
                .mapToLong(TaxResult.LineItemTax::taxAmount)
                .sum();

        return new TaxResult(BigDecimal.valueOf(totalTax), rate, lineItemTaxes);
    }

    /**
     * Rounds a decimal VND amount to the nearest 1,000₫.
     */
    static long roundTo1000(BigDecimal amount) {
        return Math.round(amount.doubleValue() / 1000.0) * 1000L;
    }

    /**
     * Returns a {@link Money} wrapping the rounded VND amount.
     */
    public static Money roundToVnd(BigDecimal amount) {
        return new Money(BigDecimal.valueOf(roundTo1000(amount)));
    }
}
