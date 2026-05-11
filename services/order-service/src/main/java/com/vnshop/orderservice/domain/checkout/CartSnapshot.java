package com.vnshop.orderservice.domain.checkout;

import java.util.List;

public record CartSnapshot(String cartId, List<CartItemSnapshot> items) {
}
