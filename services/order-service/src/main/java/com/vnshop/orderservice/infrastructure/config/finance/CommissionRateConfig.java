package com.vnshop.orderservice.infrastructure.config.finance;

import com.vnshop.orderservice.domain.finance.CommissionCalculator;
import com.vnshop.orderservice.domain.finance.CommissionTier;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vnshop.commission")
public class CommissionRateConfig implements CommissionCalculator.RateProvider {
    private Map<CommissionTier, BigDecimal> tiers = defaultRates();

    public Map<CommissionTier, BigDecimal> getTiers() {
        return tiers;
    }

    public void setTiers(Map<CommissionTier, BigDecimal> tiers) {
        this.tiers = tiers;
    }

    @Override
    public BigDecimal rateFor(CommissionTier tier) {
        BigDecimal rate = tiers.get(tier);
        if (rate == null) {
            throw new IllegalStateException("No commission rate configured for tier: " + tier);
        }
        return rate;
    }

    private static Map<CommissionTier, BigDecimal> defaultRates() {
        EnumMap<CommissionTier, BigDecimal> rates = new EnumMap<>(CommissionTier.class);
        rates.put(CommissionTier.STANDARD, new BigDecimal("0.1000"));
        rates.put(CommissionTier.VERIFIED, new BigDecimal("0.0800"));
        rates.put(CommissionTier.PREFERRED, new BigDecimal("0.0600"));
        rates.put(CommissionTier.MALL, new BigDecimal("0.0400"));
        return rates;
    }
}
