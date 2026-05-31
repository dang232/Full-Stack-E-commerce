package com.vnshop.sellerfinanceservice.domain;

import com.vnshop.sellerfinanceservice.infrastructure.config.CommissionRateConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionCalculatorTest {
    @Test
    void calculatesStandardCommissionAndSellerNet() {
        CommissionRateConfig rateConfig = new CommissionRateConfig();
        rateConfig.setTiers(Map.of(CommissionTier.STANDARD, BigDecimal.valueOf(0.10)));
        CommissionCalculator calculator = new CommissionCalculator(rateConfig);

        CommissionCalculator.CommissionBreakdown breakdown = calculator.calculate(BigDecimal.valueOf(100000), CommissionTier.STANDARD);

        assertThat(breakdown.commission()).isEqualByComparingTo("10000.00");
        assertThat(breakdown.sellerNet()).isEqualByComparingTo("90000.00");
    }
}
