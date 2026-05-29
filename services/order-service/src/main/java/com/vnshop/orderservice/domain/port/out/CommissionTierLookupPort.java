package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.CommissionTier;

public interface CommissionTierLookupPort {
    CommissionTier findBySellerId(String sellerId);
}
