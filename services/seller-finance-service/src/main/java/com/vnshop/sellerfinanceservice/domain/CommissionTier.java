package com.vnshop.sellerfinanceservice.domain;

import java.math.BigDecimal;

public enum CommissionTier {
    STANDARD("0.10"),
    VERIFIED("0.08"),
    PREFERRED("0.05"),
    MALL("0.03");

    private final BigDecimal rate;

    CommissionTier(String rate) {
        this.rate = new BigDecimal(rate);
    }

    public BigDecimal rate() {
        return rate;
    }
}
