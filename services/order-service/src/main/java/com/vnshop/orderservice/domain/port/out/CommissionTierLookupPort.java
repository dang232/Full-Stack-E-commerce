package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.CommissionTier;

import java.util.Map;
import java.util.Set;

public interface CommissionTierLookupPort {
    CommissionTier findBySellerId(String sellerId);

    Map<String, CommissionTier> findBySellerIds(Set<String> sellerIds);
}
