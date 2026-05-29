package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.CommissionTier;
import com.vnshop.orderservice.domain.Money;
import com.vnshop.orderservice.domain.Return;

public interface RefundRequestPort {
    void requestRefund(Return orderReturn, String sellerId, Money amount, CommissionTier commissionTier);
}
