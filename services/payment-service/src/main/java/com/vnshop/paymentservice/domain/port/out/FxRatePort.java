package com.vnshop.paymentservice.domain.port.out;

import java.math.BigDecimal;

/**
 * Outbound port for foreign-exchange rates. Stripe / PayPal need amounts in
 * USD; order totals are stored in VND. The port stays narrow — one method,
 * one return — so the live adapter (Frankfurter), tests, and any future
 * multi-source rotation can all sit behind the same contract.
 */
public interface FxRatePort {
    /**
     * @return the rate to multiply a {@code from}-denominated amount by to get
     *         the equivalent in {@code to}. Always positive.
     */
    BigDecimal rate(String fromCurrency, String toCurrency);
}
