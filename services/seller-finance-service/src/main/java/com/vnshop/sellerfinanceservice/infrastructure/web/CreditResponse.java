package com.vnshop.sellerfinanceservice.infrastructure.web;

import java.math.BigDecimal;

public record CreditResponse(WalletResponse wallet, BigDecimal commission, BigDecimal sellerNet) {
}
