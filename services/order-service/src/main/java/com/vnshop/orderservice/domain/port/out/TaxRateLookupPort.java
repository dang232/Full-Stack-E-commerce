package com.vnshop.orderservice.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface TaxRateLookupPort {

    /**
     * Returns the applicable VAT rate for the given category on the given date.
     * Returns empty when no rate is configured for that category.
     */
    Optional<BigDecimal> findRate(String categoryCode, LocalDate asOf);
}
