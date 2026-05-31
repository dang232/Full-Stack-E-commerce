package com.vnshop.couponservice.application;

import java.math.BigDecimal;

/**
 * Successful apply: the discount that was charged plus the resulting final
 * total. The web layer maps this onto its response DTO.
 */
public record ApplyCouponResult(String code, BigDecimal discount, BigDecimal finalTotal) {
}
