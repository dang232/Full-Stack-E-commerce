package com.vnshop.orderservice.domain.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class CommissionCalculator {
    private final RateProvider rateProvider;

    public CommissionCalculator(RateProvider rateProvider) {
        this.rateProvider = Objects.requireNonNull(rateProvider, "rateProvider is required");
    }

    public CommissionBreakdown calculate(BigDecimal originalOrderAmount, CommissionTier tier) {
        Objects.requireNonNull(originalOrderAmount, "originalOrderAmount is required");
        Objects.requireNonNull(tier, "tier is required");
        if (originalOrderAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("originalOrderAmount must not be negative");
        }
        BigDecimal commission = originalOrderAmount.multiply(rateProvider.rateFor(tier)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellerNet = originalOrderAmount.subtract(commission).setScale(2, RoundingMode.HALF_UP);
        return new CommissionBreakdown(commission, sellerNet);
    }

    public interface RateProvider {
        BigDecimal rateFor(CommissionTier tier);
    }

    public record CommissionBreakdown(BigDecimal commission, BigDecimal sellerNet) {
    }
}
