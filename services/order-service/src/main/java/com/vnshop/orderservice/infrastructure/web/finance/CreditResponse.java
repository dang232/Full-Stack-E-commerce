package com.vnshop.orderservice.infrastructure.web.finance;

import java.math.BigDecimal;

public record CreditResponse(WalletResponse wallet, BigDecimal commission, BigDecimal sellerNet) {
}
